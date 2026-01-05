package de.tomalbrc.dynamo.impl.physics;

import com.github.stephengold.joltjni.PhysicsSystem;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class PhysicsThread implements AutoCloseable {
    private final Queue<Consumer<PhysicsSystem>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    public final Thread thread;

    private static final long SLEEP_NANOS = 16_666_667L;

    public PhysicsThread() {
        this.thread = new Thread(this::runLoop, "Dynamo-PhysicsThread");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    synchronized public void enqueue(Consumer<PhysicsSystem> task) {
        if (!this.running.get())
            return;

        this.queue.add(task);
    }

    private void runLoop() {
        long lastTime = System.nanoTime();

        while (running.get()) {
            long now = System.nanoTime();
            float deltaTime = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

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
