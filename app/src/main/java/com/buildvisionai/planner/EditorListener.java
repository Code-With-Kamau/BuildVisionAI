package com.buildvisionai.planner;

/** Callbacks from the editor core to the UI. All are invoked on the thread that feeds input. */
public interface EditorListener {
    /** Something visible changed: redraw. */
    void onInvalidate();

    /** The document changed through an edit, undo or redo (refresh counts, estimates, titles). */
    void onDocumentChanged();

    void onSelectionChanged();

    /** The active tool changed (e.g. a tool finished and returned to Select). */
    void onToolChanged();

    /** Short user-facing message (snackbar), e.g. "No room on this wall". */
    void onMessage(String text);

    /** The user tapped a room with the Room tool: show its name/type dialog. */
    void onRoomEditRequested(String roomId);
}
