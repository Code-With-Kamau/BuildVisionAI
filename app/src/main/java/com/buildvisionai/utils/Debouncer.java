package com.buildvisionai.utils;

/**
 * Runs a task once after a burst of requests has gone quiet. The editor asks for a save on every
 * change; dragging a wall produces dozens per second, and only the last one matters. {@link #flush()}
 * runs a pending task straight away (call it when the screen is paused, so nothing is lost).
 *
 * <p>The clock is injected through {@link Timer} so the behaviour is tested without sleeping. Not
 * thread-safe: call it from one thread (the main thread, in the app).
 */
public final class Debouncer {
    /** Schedules a task after a delay and returns a handle that cancels it. */
    public interface Timer {
        Runnable schedule(Runnable task, long delayMs);
    }

    private final Timer timer;
    private final long delayMs;
    private Runnable cancel;
    private Runnable pending;

    public Debouncer(Timer timer, long delayMs) {
        this.timer = timer;
        this.delayMs = delayMs;
    }

    /** Replaces any pending task with this one and restarts the quiet period. */
    public void submit(Runnable task) {
        if (cancel != null) cancel.run();
        pending = task;
        cancel = timer.schedule(this::fire, delayMs);
    }

    /** Runs the pending task now, if there is one. */
    public void flush() {
        if (pending == null) return;
        if (cancel != null) cancel.run();
        fire();
    }

    public boolean hasPending() {
        return pending != null;
    }

    private void fire() {
        Runnable task = pending;
        pending = null;
        cancel = null;
        if (task != null) task.run();
    }
}
