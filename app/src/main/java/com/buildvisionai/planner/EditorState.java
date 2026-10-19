package com.buildvisionai.planner;

import com.buildvisionai.models.ColumnShape;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WindowType;
import com.buildvisionai.utils.UnitSystem;

/** Current tool, per-tool options and display toggles. Held by the ViewModel across rotation. */
public final class EditorState {
    public ToolType tool = ToolType.SELECT;
    public WallKind wallKind = WallKind.EXTERNAL;
    public DoorType doorType = DoorType.SINGLE;
    public WindowType windowType = WindowType.SLIDING;
    public ColumnShape columnShape = ColumnShape.SQUARE;
    public StairType stairType = StairType.STRAIGHT;
    public FurnitureType furnitureType = FurnitureType.values()[0];
    public boolean showGrid = true;
    public boolean showDimensions = true;
    public boolean showRoomLabels = true;
    /** Taps add to / remove from the selection instead of replacing it. */
    public boolean multiSelect = false;
    public UnitSystem units = UnitSystem.METRIC;
    public final SnapOptions snap = new SnapOptions();
}
