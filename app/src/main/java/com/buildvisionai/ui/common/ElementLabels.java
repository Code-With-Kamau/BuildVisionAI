package com.buildvisionai.ui.common;

import android.content.Context;

import com.buildvisionai.R;
import com.buildvisionai.models.ElementType;
import com.buildvisionai.models.WorkflowState;

/** Translated names for plan element types and workflow states. */
public final class ElementLabels {
    private ElementLabels() {}

    public static String of(Context c, ElementType t) {
        switch (t) {
            case WALL: return c.getString(R.string.element_wall);
            case ROOM: return c.getString(R.string.element_room);
            case DOOR: return c.getString(R.string.element_door);
            case WINDOW: return c.getString(R.string.element_window);
            case COLUMN: return c.getString(R.string.element_column);
            case STAIR: return c.getString(R.string.element_stair);
            default: return c.getString(R.string.element_furniture);
        }
    }

    public static String of(Context c, WorkflowState s) {
        switch (s) {
            case PROFESSIONAL_REVIEW: return c.getString(R.string.workflow_professional_review);
            case OWNER_REVIEW: return c.getString(R.string.workflow_owner_review);
            case APPROVED: return c.getString(R.string.workflow_approved);
            case LOCKED: return c.getString(R.string.workflow_locked);
            default: return c.getString(R.string.workflow_draft);
        }
    }
}
