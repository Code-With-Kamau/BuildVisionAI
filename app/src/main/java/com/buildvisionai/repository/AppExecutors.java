package com.buildvisionai.repository;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One background thread for all database work, and a way back to the main thread. A single thread
 * means writes happen in the order they were requested (an autosave can never overtake the one
 * before it) and SQLite never sees two writers; reads are cheap enough to share it. Compute-heavy
 * work (topology, estimation of a big plan) should not run here: give it its own executor.
 */
public final class AppExecutors {
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "buildvision-io");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());

    public void io(Runnable work) {
        io.execute(work);
    }

    public void main(Runnable work) {
        main.post(work);
    }

    /** Runs {@code work} on the io thread and reports the outcome on the main thread. */
    public <T> void run(Callable<T> work, RepoCallback<T> callback) {
        io.execute(() -> {
            try {
                T result = work.call();
                main.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                main.post(() -> callback.onError(e));
            }
        });
    }
}
