package com.buildvisionai.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.Door;
import com.buildvisionai.models.DoorType;
import com.buildvisionai.models.Vec2;
import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.models.Window;
import com.buildvisionai.models.WindowType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryTest {
    static int seq = 0;

    static Wall wall(double x1, double y1, double x2, double y2, WallKind k) {
        return new Wall("w" + (seq++), "f0", x1, y1, x2, y2, k);
    }

    static List<Wall> rect(double w, double h) {
        return new ArrayList<>(Arrays.asList(
                wall(0, 0, w, 0, WallKind.EXTERNAL),
                wall(w, 0, w, h, WallKind.EXTERNAL),
                wall(w, h, 0, h, WallKind.EXTERNAL),
                wall(0, h, 0, 0, WallKind.EXTERNAL)));
    }

    @Test public void signedAreaAndPointInPolygon() {
        List<Vec2> sq = Arrays.asList(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10), new Vec2(0, 10));
        assertEquals(100, GeoUtils.signedArea(sq), 1e-9);
        assertTrue(GeoUtils.pointInPolygon(5, 5, sq));
        assertFalse(GeoUtils.pointInPolygon(15, 5, sq));
        assertEquals(5, GeoUtils.distToSegment(5, 5, 0, 0, 10, 0), 1e-9);
    }

    @Test public void interiorPointOfLShapeIsInside() {
        List<Vec2> l = Arrays.asList(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 1), new Vec2(1, 1),
                new Vec2(1, 10), new Vec2(0, 10));
        Vec2 p = GeoUtils.interiorPoint(l);
        assertTrue(GeoUtils.pointInPolygon(p.x, p.y, l));
    }

    @Test public void singleRoomNetAndGrossArea() {
        PlanTopology t = PlanTopology.build(rect(5000, 4000));
        assertEquals(1, t.rooms.size());
        DetectedRoom r = t.rooms.get(0);
        assertEquals(20.0, r.grossAreaM2, 1e-6);
        assertEquals(4.8 * 3.8, r.netAreaM2, 1e-6);       // 200 mm walls: 100 mm each side
        assertTrue(r.netExact);
        assertEquals(2 * (4.8 + 3.8), r.netPerimeterM, 1e-6);
        assertEquals(3.8, r.widthM, 1e-6);
        assertEquals(4.8, r.lengthM, 1e-6);
        assertEquals(4, r.wallIds.size());
        assertTrue(r.contains(2500, 2000));
        assertFalse(r.contains(6000, 2000));
    }

    @Test public void footprintIncludesWallThickness() {
        PlanTopology t = PlanTopology.build(rect(5000, 4000));
        assertEquals(1, t.footprints.size());
        assertEquals(5.2 * 4.2, t.footprintAreaM2, 1e-6);
        assertEquals(2 * (5.2 + 4.2), t.externalPerimeterM, 1e-6);
    }

    @Test public void partitionSplitsRoomIntoTwo() {
        List<Wall> ws = rect(5000, 4000);
        ws.add(wall(3000, 0, 3000, 4000, WallKind.INTERNAL)); // 150 mm, T-junctions at both ends
        PlanTopology t = PlanTopology.build(ws);
        assertEquals(2, t.rooms.size());
        double left = 0, right = 0;
        for (DetectedRoom r : t.rooms) {
            if (r.interior.x < 3000) left = r.netAreaM2; else right = r.netAreaM2;
        }
        assertEquals(2.825 * 3.8, left, 1e-6);
        assertEquals(1.825 * 3.8, right, 1e-6);
        // the top wall is bounding both rooms, so it appears in both rooms' wall lists
        assertEquals(1, t.footprints.size());
        assertEquals(5.2 * 4.2, t.footprintAreaM2, 1e-6);
    }

    @Test public void crossingWallsMakeFourRooms() {
        List<Wall> ws = rect(6000, 6000);
        ws.add(wall(3000, 0, 3000, 6000, WallKind.INTERNAL));
        ws.add(wall(0, 3000, 6000, 3000, WallKind.INTERNAL)); // crosses the first one mid-way
        PlanTopology t = PlanTopology.build(ws);
        assertEquals(4, t.rooms.size());
        double each = (3.0 - 0.1 - 0.075) * (3.0 - 0.1 - 0.075);
        for (DetectedRoom r : t.rooms) assertEquals(each, r.netAreaM2, 1e-6);
    }

    @Test public void danglingWallIsIgnored() {
        List<Wall> ws = rect(5000, 4000);
        ws.add(wall(5000, 2000, 7000, 2000, WallKind.EXTERNAL)); // sticks out of the building
        ws.add(wall(2000, 1000, 2000, 2500, WallKind.PARTITION)); // stub inside, ends in the room
        PlanTopology t = PlanTopology.build(ws);
        assertEquals(1, t.rooms.size());
        assertEquals(4.8 * 3.8, t.rooms.get(0).netAreaM2, 1e-6);
    }

    @Test public void openChainIsNotARoom() {
        List<Wall> ws = rect(5000, 4000);
        ws.remove(3); // leave a gap
        assertEquals(0, PlanTopology.build(ws).rooms.size());
        assertNull(PlanTopology.build(ws).roomContaining(2500, 2000));
    }

    @Test public void lShapedRoomArea() {
        List<Wall> ws = new ArrayList<>(Arrays.asList(
                wall(0, 0, 6000, 0, WallKind.EXTERNAL),
                wall(6000, 0, 6000, 2000, WallKind.EXTERNAL),
                wall(6000, 2000, 2000, 2000, WallKind.EXTERNAL),
                wall(2000, 2000, 2000, 5000, WallKind.EXTERNAL),
                wall(2000, 5000, 0, 5000, WallKind.EXTERNAL),
                wall(0, 5000, 0, 0, WallKind.EXTERNAL)));
        PlanTopology t = PlanTopology.build(ws);
        assertEquals(1, t.rooms.size());
        DetectedRoom r = t.rooms.get(0);
        assertEquals(6.0 * 2.0 + 2.0 * 3.0, r.grossAreaM2, 1e-6);
        // L-shape with 100 mm inset on every side: net = gross - perimeter*0.1 + 6 convex-corner... compute exactly:
        // offset polygon of an L with 6 convex + 1 reflex corner: area = gross - 0.1*perimeter + 0.01*(convex - reflex)
        double per = 6 + 2 + 4 + 3 + 2 + 5;
        double expected = 18.0 - 0.1 * per + 0.01 * (5 - 1);
        assertEquals(expected, r.netAreaM2, 1e-6);
        assertTrue(r.netExact);
        Vec2 p = r.interior;
        assertTrue(GeoUtils.pointInPolygon(p.x, p.y, r.netPolygon));
    }

    @Test public void roomNarrowerThanWallFallsBackSafely() {
        List<Wall> ws = rect(300, 4000); // 300 mm centreline gap, 200 mm walls: net 100 mm wide
        PlanTopology t = PlanTopology.build(ws);
        assertEquals(1, t.rooms.size());
        assertTrue(t.rooms.get(0).netAreaM2 >= 0);
    }

    @Test public void openingsAreDeductedFromWallAndRoomSurfaces() {
        List<Wall> ws = rect(5000, 4000);
        Wall bottom = ws.get(2);
        Door d = new Door("d1", "f0", bottom.id, 1000, DoorType.SINGLE);
        Window w = new Window("x1", "f0", ws.get(0).id, 2500, WindowType.SLIDING);
        Map<String, Wall> byId = new HashMap<>();
        for (Wall x : ws) byId.put(x.id, x);
        GeometryEngine.resolveOpenings(byId, Arrays.asList(d), Arrays.asList(w));
        assertTrue(d.attached);
        assertTrue(w.attached);
        PlanMetrics pm = GeometryEngine.analyse(ws, Arrays.asList(d), Arrays.asList(w));
        assertEquals(1, pm.doorCount);
        assertEquals(1, pm.windowCount);
        double doorArea = 0.9 * 2.1, winArea = 1.5 * 1.2;
        assertEquals(2 * (5 + 4) * 2.7 - 0, pm.walls.stream().mapToDouble(m -> m.grossFaceAreaM2).sum(), 1e-9);
        assertEquals(doorArea + winArea,
                pm.walls.stream().mapToDouble(m -> m.openingAreaM2).sum(), 1e-9);
        RoomMetrics rm = pm.rooms.get(0);
        assertEquals(doorArea + winArea, rm.openingAreaM2, 1e-9);
        assertEquals(rm.grossWallSurfaceM2 - doorArea - winArea, rm.netWallSurfaceM2, 1e-9);
        assertEquals(2.7, rm.wallHeightM, 1e-9);
        assertEquals(1, rm.doorCount);
        assertEquals(1, rm.windowCount);
    }

    @Test public void openingOnTooShortWallIsUnattached() {
        Wall stub = wall(0, 0, 500, 0, WallKind.PARTITION);
        Door d = new Door("d1", "f0", stub.id, 250, DoorType.DOUBLE); // 1500 wide on a 500 wall
        Map<String, Wall> byId = new HashMap<>();
        byId.put(stub.id, stub);
        GeometryEngine.resolveOpenings(byId, Arrays.asList(d), new ArrayList<Window>());
        assertFalse(d.attached);
    }

    @Test public void offsetPolygonOps() {
        List<Vec2> sq = Arrays.asList(new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 100), new Vec2(0, 100));
        List<Vec2> in = PolygonOps.offsetUniform(sq, 10);
        assertEquals(80 * 80, GeoUtils.area(in), 1e-9);
        List<Vec2> out = PolygonOps.offsetUniform(sq, -10);
        assertEquals(120 * 120, GeoUtils.area(out), 1e-9);
        assertTrue(PolygonOps.isSimple(sq));
        List<Vec2> bow = Arrays.asList(new Vec2(0, 0), new Vec2(10, 10), new Vec2(10, 0), new Vec2(0, 10));
        assertFalse(PolygonOps.isSimple(bow));
        double[] ext = PolygonOps.orientedExtents(Arrays.asList(new Vec2(0, 0), new Vec2(40, 0), new Vec2(40, 10), new Vec2(0, 10)));
        assertEquals(10, ext[0], 1e-9);
        assertEquals(40, ext[1], 1e-9);
    }

    @Test public void spatialGridFindsNearbyOnly() {
        SpatialGrid<String> g = new SpatialGrid<>(1000);
        g.insert("a", new com.buildvisionai.models.RectD(0, 0, 500, 500));
        g.insert("b", new com.buildvisionai.models.RectD(50000, 50000, 50500, 50500));
        List<String> hits = g.queryPoint(100, 100, 200);
        assertEquals(1, hits.size());
        assertEquals("a", hits.get(0));
        assertNotNull(g.query(new com.buildvisionai.models.RectD(-1e9, -1e9, 1e9, 1e9)));
    }
}
