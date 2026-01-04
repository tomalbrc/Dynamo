package de.tomalbrc.dynamo.impl.physics;

import com.jme3.bullet.PhysicsSpace;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class PhysicsThread implements AutoCloseable {
    private PhysicsSpace physicsSpace;
    private final Queue<Consumer<PhysicsSpace>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    public final Thread thread;

    private static final long SLEEP_NANOS = 16_666_667L;

    public PhysicsThread() {
        this.thread = new Thread(this::runLoop, "Dynamo-PhysicsThread");
        this.thread.setDaemon(true);

        this.enqueue(k -> this.physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT));
        this.thread.start();

        while (this.physicsSpace == null){
            LockSupport.parkNanos(100_000);
        }
    }

    public PhysicsSpace getPhysicsSpace() {
        return this.physicsSpace;
    }

    synchronized public void enqueue(Consumer<PhysicsSpace> task) {
        if (!this.running.get())
            return;

        this.queue.add(task);
    }

    private void runLoop() {
        long lastTime = System.nanoTime();

        while (running.get()) {
            long now = System.nanoTime();
            // Calculate the actual time passed since the last loop started
            float deltaTime = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            // Process the queue
            Consumer<PhysicsSpace> r;
            while ((r = queue.poll()) != null) {
                try {
                    r.accept(physicsSpace);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

            if (this.physicsSpace != null) {
                // Use the actual measured deltaTime
                // We cap it to avoid "the spiral of death" if the window is moved
                // or the thread is suspended (e.g., 0.1s max)
                float tpf = Math.min(deltaTime, 0.1f);
                this.physicsSpace.update(tpf, 4);
            }

            // Calculate how much time we spent doing work to determine sleep duration
            long workDoneTime = System.nanoTime();
            long elapsedWork = workDoneTime - now;
            long sleep = SLEEP_NANOS - elapsedWork;

            if (sleep > 0) {
                LockSupport.parkNanos(sleep);
            }
        }
    }

    @Override
    public void close() {
        this.running.set(false);
        this.thread.interrupt();
        try {
            this.thread.join();
        } catch (InterruptedException ignored) {
        }
    }
}
