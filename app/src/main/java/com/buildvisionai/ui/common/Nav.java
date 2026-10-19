package com.buildvisionai.ui.common;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

/**
 * Navigation that survives a double tap. The first tap moves the app to the next screen; a second tap that lands
 * before the old screen has gone would ask the controller for an action the new current screen does not have, and
 * that throws. This ignores the second tap.
 */
public final class Nav {
    private Nav() {}

    /** Runs {@code actionId} from the screen that {@code from} is part of, unless the app has already left it. */
    public static void go(Fragment from, int actionId, Bundle args) {
        NavController controller = NavHostFragment.findNavController(from);
        NavDestination here = controller.getCurrentDestination();
        if (here == null || here.getAction(actionId) == null) return;
        controller.navigate(actionId, args);
    }
}
