package com.buildvisionai.planner;

import com.buildvisionai.models.PlacedElement;
import com.buildvisionai.models.PlanElement;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;

/** Where each handle of an element sits in plan space; shared by hit-testing and drawing. */
public final class HandleGeometry {
    private HandleGeometry() {}

    public static Handle[] handlesOf(PlanElement e) {
        if (e instanceof Wall) return new Handle[]{Handle.WALL_START, Handle.WALL_END};
        if (e instanceof PlacedElement) {
            return new Handle[]{Handle.CORNER_NW, Handle.CORNER_NE, Handle.CORNER_SE, Handle.CORNER_SW, Handle.ROTATE};
        }
        return new Handle[0];
    }

    /** @param rotateOffsetMm distance of the rotate handle beyond the top edge (zoom dependent). */
    public static Vec2 position(PlanElement e, Handle h, double rotateOffsetMm) {
        if (e instanceof Wall) {
            Wall w = (Wall) e;
            return h == Handle.WALL_START ? w.start() : w.end();
        }
        if (e instanceof PlacedElement) {
            PlacedElement p = (PlacedElement) e;
            Vec2[] c = p.corners();
            switch (h) {
                case CORNER_NW: return c[0];
                case CORNER_NE: return c[1];
                case CORNER_SE: return c[2];
                case CORNER_SW: return c[3];
                case ROTATE: return p.toWorld(0, -p.depthMm / 2 - rotateOffsetMm);
                default: break;
            }
        }
        return null;
    }
}
