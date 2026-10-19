package com.buildvisionai.planner.tool;

import com.buildvisionai.models.Vec2;
import com.buildvisionai.planner.EditTransaction;
import com.buildvisionai.planner.EditorState;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.planner.Preview;
import com.buildvisionai.planner.RoomSync;
import com.buildvisionai.planner.Selection;
import com.buildvisionai.planner.SnapResult;
import com.buildvisionai.planner.ToolType;
import com.buildvisionai.planner.ViewTransform;

import java.util.Set;

/** What a {@link Tool} may do. Implemented by EditorController; tests can fake it. */
public interface EditorContext {
    PlanDocument doc();

    String floorId();

    /** Snapshot of the active floor for the current document revision. */
    FloorPlan plan();

    Selection selection();

    ViewTransform view();

    EditorState state();

    Preview preview();

    boolean canEdit();

    /** Touch tolerance in screen pixels. */
    double touchPx();

    SnapResult snap(Vec2 raw, Vec2 anchor, Set<String> exclude);

    /**
     * Records a user action. If {@code alreadyApplied} the document already reflects it (live
     * drag), otherwise the transaction is applied first. Rooms are re-synced either way.
     */
    RoomSync.Result commit(EditTransaction tx, boolean alreadyApplied);

    void message(String text);

    void invalidate();

    void selectionChanged();

    void requestRoomEdit(String roomId);

    void setTool(ToolType tool);
}
