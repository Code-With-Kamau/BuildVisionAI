package com.buildvisionai.estimation;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Furniture;
import com.buildvisionai.models.FurnitureType;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.RoomType;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.Window;
import com.buildvisionai.models.WindowType;
import com.buildvisionai.planner.EditTransaction;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.planner.RoomSync;

import java.util.ArrayList;
import java.util.List;

/** Programmatic reference plans for the estimator tests. */
final class TestPlans {
    private TestPlans() {}

    static int seq = 0;

    static Wall wall(PlanDocument d, String floor, double x1, double y1, double x2, double y2, WallKind k) {
        Wall w = new Wall("w" + (seq++), floor, x1, y1, x2, y2, k);
        d.put(w);
        return w;
    }

    /** Offset along wall (mm) of the point (x, y) on it. */
    static double off(Wall w, double x, double y) { return Math.hypot(x - w.startX, y - w.startY); }

    static void door(PlanDocument d, String floor, Wall w, double x, double y, DoorType t) {
        d.put(new Door("d" + (seq++), floor, w.id, off(w, x, y), t));
    }

    static void window(PlanDocument d, String floor, Wall w, double x, double y, WindowType t) {
        d.put(new Window("x" + (seq++), floor, w.id, off(w, x, y), t));
    }

    static void typeRoom(PlanDocument d, String floor, double x, double y, String name, RoomType t) {
        com.buildvisionai.planner.FloorPlan fp = d.floorPlan(floor);
        DetectedRoom shape = fp.topology().roomContaining(x, y);
        for (Room r : fp.rooms) {
            if (fp.shapeOf(r) == shape) {
                Room c = r.copy();
                c.name = name;
                c.roomType = t;
                d.put(c);
                return;
            }
        }
        throw new IllegalStateException("no room at " + x + "," + y);
    }

    /** 12 x 10 m (centreline) three-bedroom bungalow, seven rooms, about 120 m2 gross. */
    static PlanDocument bungalow() {
        PlanDocument d = new PlanDocument();
        Floor f = new Floor("g", 0, "Ground Floor");
        d.putFloor(f);
        String g = f.id;
        Wall top = wall(d, g, 0, 0, 12000, 0, WallKind.EXTERNAL);
        Wall right = wall(d, g, 12000, 0, 12000, 10000, WallKind.EXTERNAL);
        Wall bottom = wall(d, g, 12000, 10000, 0, 10000, WallKind.EXTERNAL);
        Wall left = wall(d, g, 0, 10000, 0, 0, WallKind.EXTERNAL);
        Wall v5 = wall(d, g, 5000, 0, 5000, 10000, WallKind.INTERNAL);
        Wall h6 = wall(d, g, 0, 6000, 5000, 6000, WallKind.INTERNAL);
        Wall v85 = wall(d, g, 8500, 0, 8500, 10000, WallKind.INTERNAL);
        Wall h5 = wall(d, g, 5000, 5000, 12000, 5000, WallKind.INTERNAL);
        Wall h75 = wall(d, g, 8500, 7500, 12000, 7500, WallKind.PARTITION);

        door(d, g, top, 2500, 0, DoorType.SINGLE);          // front door
        door(d, g, bottom, 2500, 10000, DoorType.SINGLE);   // back door
        door(d, g, h6, 2500, 6000, DoorType.SINGLE);        // living <-> kitchen
        door(d, g, h5, 6000, 5000, DoorType.SINGLE);        // bedroom 1
        door(d, g, h5, 7500, 5000, DoorType.SINGLE);        // bedroom 3
        door(d, g, h5, 10000, 5000, DoorType.SINGLE);       // master
        door(d, g, v85, 8500, 6250, DoorType.SINGLE);       // bathroom
        door(d, g, v85, 8500, 8750, DoorType.SINGLE);       // toilet

        window(d, g, left, 0, 8500, WindowType.SLIDING);
        window(d, g, left, 0, 5500, WindowType.SLIDING);
        window(d, g, left, 0, 2000, WindowType.SLIDING);
        window(d, g, top, 6750, 0, WindowType.SLIDING);
        window(d, g, top, 10250, 0, WindowType.SLIDING);
        window(d, g, bottom, 6750, 10000, WindowType.SLIDING);
        window(d, g, right, 12000, 6250, WindowType.CASEMENT);
        window(d, g, right, 12000, 8750, WindowType.CASEMENT);

        RoomSync.sync(d, g, new EditTransaction("rooms"));
        typeRoom(d, g, 2500, 3000, "Living Room", RoomType.LIVING_ROOM);
        typeRoom(d, g, 2500, 8000, "Kitchen / Dining", RoomType.KITCHEN);
        typeRoom(d, g, 6750, 2500, "Bedroom 1", RoomType.BEDROOM);
        typeRoom(d, g, 10250, 2500, "Master Bedroom", RoomType.MASTER_BEDROOM);
        typeRoom(d, g, 6750, 7500, "Bedroom 3", RoomType.BEDROOM);
        typeRoom(d, g, 10250, 6250, "Bathroom", RoomType.BATHROOM);
        typeRoom(d, g, 10250, 8750, "Toilet", RoomType.TOILET);
        return d;
    }

    static List<PlanElement> ofType(PlanDocument d, Class<?> c) {
        List<PlanElement> l = new ArrayList<>();
        for (PlanElement e : d.all()) if (c.isInstance(e)) l.add(e);
        return l;
    }

    static Furniture furniture(PlanDocument d, String floor, double x, double y, FurnitureType t) {
        Furniture f = new Furniture("f" + (seq++), floor, x, y, t);
        d.put(f);
        return f;
    }
}
