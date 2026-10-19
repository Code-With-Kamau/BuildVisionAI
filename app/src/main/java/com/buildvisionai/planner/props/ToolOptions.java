package com.buildvisionai.planner.props;

import com.buildvisionai.models.ColumnShape;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.WindowType;
import com.buildvisionai.planner.EditorState;
import com.buildvisionai.planner.ToolType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The choices shown above the tool bar for the active tool: which wall type to draw, which door or
 * window type to place, and so on. Pure data so the mapping from a tool to its options, and from
 * a chosen option back to the editor state, is tested without a device.
 */
public final class ToolOptions {
    private ToolOptions() {}

    public static final class Option {
        /** Enum constant name; stable across languages and releases. */
        public final String id;
        public final String label;
        public final boolean selected;

        Option(String id, String label, boolean selected) {
            this.id = id;
            this.label = label;
            this.selected = selected;
        }
    }

    /** True when the tool has options to choose from. */
    public static boolean hasOptions(ToolType tool) {
        switch (tool) {
            case WALL: case DOOR: case WINDOW: case COLUMN: case STAIR: case FURNITURE: return true;
            default: return false;
        }
    }

    public static List<Option> of(ToolType tool, EditorState s) {
        List<Option> out = new ArrayList<>();
        switch (tool) {
            case WALL:
                for (WallKind k : WallKind.values()) out.add(new Option(k.name(), k.label, k == s.wallKind));
                break;
            case DOOR:
                for (DoorType k : DoorType.values()) out.add(new Option(k.name(), k.label, k == s.doorType));
                break;
            case WINDOW:
                for (WindowType k : WindowType.values()) out.add(new Option(k.name(), k.label, k == s.windowType));
                break;
            case COLUMN:
                for (ColumnShape k : ColumnShape.values()) out.add(new Option(k.name(), k.label, k == s.columnShape));
                break;
            case STAIR:
                for (StairType k : StairType.values()) out.add(new Option(k.name(), k.label, k == s.stairType));
                break;
            case FURNITURE:
                for (FurnitureType k : FurnitureType.values()) out.add(new Option(k.name(), k.label, k == s.furnitureType));
                break;
            default:
                return Collections.emptyList();
        }
        return out;
    }

    /** Applies the user's choice to the editor state. Unknown ids and tools without options change nothing. */
    public static boolean select(ToolType tool, EditorState s, String id) {
        try {
            switch (tool) {
                case WALL: s.wallKind = WallKind.valueOf(id); return true;
                case DOOR: s.doorType = DoorType.valueOf(id); return true;
                case WINDOW: s.windowType = WindowType.valueOf(id); return true;
                case COLUMN: s.columnShape = ColumnShape.valueOf(id); return true;
                case STAIR: s.stairType = StairType.valueOf(id); return true;
                case FURNITURE: s.furnitureType = FurnitureType.valueOf(id); return true;
                default: return false;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
