package com.buildvisionai.ui.common;

import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;

import java.util.Arrays;
import java.util.List;

/**
 * Helpers for the exposed dropdowns (AutoCompleteTextView). A dropdown reports a tap as a position in the
 * list it is showing, and that list is filtered by the text in the box. After a rotation the framework puts
 * the saved text back and the popup then lists only the rows that match it, so a position can no longer be
 * used as an index into the full list of choices: it would pick the wrong enum value without any sign of it.
 * Choices are therefore read back by their label.
 */
public final class Dropdowns {
    private Dropdowns() {}

    /** Index in the full list of the row the person tapped, or -1 when it cannot be told. */
    public static int indexOfTapped(AdapterView<?> parent, int position, List<String> labels) {
        Object item = parent == null || position < 0 ? null : parent.getItemAtPosition(position);
        return item == null ? -1 : labels.indexOf(item.toString());
    }

    public static int indexOfTapped(AdapterView<?> parent, int position, String[] labels) {
        return indexOfTapped(parent, position, Arrays.asList(labels));
    }

    /**
     * For a dropdown whose text always comes from the model (set in code on every render): stop the framework
     * restoring its text, which would filter the popup down to one row. The screen shows the model's value again
     * when it is rebuilt.
     */
    public static void textComesFromModel(AutoCompleteTextView... views) {
        for (AutoCompleteTextView v : views) v.setSaveEnabled(false);
    }
}
