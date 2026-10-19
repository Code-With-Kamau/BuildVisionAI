package com.buildvisionai.ui.common;

import android.os.Handler;
import android.os.Looper;

import com.buildvisionai.utils.Debouncer;

/** The real clock for {@link Debouncer}: posts to the main thread and can cancel. */
public final class MainThreadTimer implements Debouncer.Timer {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public Runnable schedule(Runnable task, long delayMs) {
        handler.postDelayed(task, delayMs);
        return () -> handler.removeCallbacks(task);
    }
}
