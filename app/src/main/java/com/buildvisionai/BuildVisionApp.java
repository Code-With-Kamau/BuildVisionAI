package com.buildvisionai;

import android.app.Application;
import android.content.Context;

/** Application class: owns the dependency container. There is no DI framework; see {@link AppContainer}. */
public class BuildVisionApp extends Application {
    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    public AppContainer container() { return container; }

    /** The container for any context in this app. */
    public static AppContainer of(Context context) {
        return ((BuildVisionApp) context.getApplicationContext()).container();
    }
}
