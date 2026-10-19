package com.buildvisionai.planner.tool;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.models.Room;

/**
 * Names and types rooms. Rooms are created automatically when walls close a loop, so this tool
 * only picks the room under the finger and asks the UI to open its name/type editor.
 */
public final class RoomTool extends Tool {
    @Override public boolean onTap(EditorContext c, double wx, double wy) {
        DetectedRoom shape = c.plan().topology().roomContaining(wx, wy);
        if (shape == null) {
            c.message("Tap inside a closed room. Close the wall loop first.");
            return true;
        }
        for (Room r : c.plan().rooms) {
            if (c.plan().shapeOf(r) == shape) {
                c.selection().set(r.id);
                c.selectionChanged();
                if (c.canEdit()) c.requestRoomEdit(r.id);
                else c.message("This version is locked");
                return true;
            }
        }
        return true;
    }
}
