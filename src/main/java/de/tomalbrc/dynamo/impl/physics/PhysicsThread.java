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
    private final Thread thread;

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
        float lastElapsed = 1f/60f;

        while (running.get()) {
            Consumer<PhysicsSpace> r;
            while ((r = queue.poll()) != null) {
                try {
                    r.accept(physicsSpace);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

            if (this.physicsSpace != null) {
                this.physicsSpace.update(1f/60f, 4);
            }

            long now = System.nanoTime();
            long elapsed = now - lastTime;
            long sleep = SLEEP_NANOS - elapsed;
            lastTime = now;
            //lastElapsed = elapsed;

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
