package com.buildvisionai.planner;

import com.buildvisionai.models.Column;
import com.buildvisionai.models.ColumnShape;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.StairType;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.Window;
import com.buildvisionai.models.WindowType;
import com.buildvisionai.utils.Ids;

/** Creates new elements with fresh ids and the defaults from the catalogue enums. */
public final class ElementFactory {
    private ElementFactory() {}

    public static Wall wall(String floorId, double x1, double y1, double x2, double y2, WallKind kind) {
        return new Wall(Ids.newId(), floorId, x1, y1, x2, y2, kind);
    }

    public static Door door(String floorId, String wallId, double offsetMm, DoorType t) {
        return new Door(Ids.newId(), floorId, wallId, offsetMm, t);
    }

    public static Window window(String floorId, String wallId, double offsetMm, WindowType t) {
        return new Window(Ids.newId(), floorId, wallId, offsetMm, t);
    }

    public static Column column(String floorId, double x, double y, ColumnShape s) {
        return new Column(Ids.newId(), floorId, x, y, s);
    }

    public static Stair stair(String floorId, double x, double y, StairType t) {
        return new Stair(Ids.newId(), floorId, x, y, t);
    }

    public static Furniture furniture(String floorId, double x, double y, FurnitureType t) {
        return new Furniture(Ids.newId(), floorId, x, y, t);
    }

    public static Room room(String floorId, String name, RoomType t, double ax, double ay) {
        return new Room(Ids.newId(), floorId, name, t, ax, ay);
    }
}
