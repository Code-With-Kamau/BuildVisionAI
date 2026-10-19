package com.buildvisionai.planner.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.models.Wall;
import com.buildvisionai.models.WallKind;
import com.buildvisionai.planner.ViewTransform;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RenderMathTest {
    static Wall wall(double x1, double y1, double x2, double y2) {
        Wall w = new Wall("w" + x1 + y1 + x2 + y2, "g", x1, y1, x2, y2, WallKind.EXTERNAL);
        w.thicknessMm = 200;
        return w;
    }

    // ---- grid ---------------------------------------------------------------------------------

    private static ViewTransform view(double scale, double tx, double ty, int w, int h) {
        ViewTransform v = new ViewTransform();
        v.scale = scale; v.tx = tx; v.ty = ty; v.setViewSize(w, h);
        return v;
    }

    @Test public void gridLinesLandOnMultiplesOfTheStepAndMajorsAreNotRepeated() {
        ViewTransform v = view(0.1, 0, 0, 1000, 800);              // 10 m x 8 m visible, 0.1 px per mm
        GridLayout g = new GridLayout();
        g.compute(v, 500, 5000);                                   // 0.5 m minor, 5 m major
        // vertical lines at x = 0, 500, ..., 10000 -> 21; majors at 0, 5000, 10000 -> 3
        // horizontal: y = 0..8000 step 500 -> 17; majors at 0, 5000 -> 2
        assertEquals((21 - 3 + 17 - 2) * 4, g.minorCount);
        assertEquals((3 + 2) * 4, g.majorCount);
        for (int i = 0; i < g.majorCount; i += 4) {
            double sx = g.major[i], sy = g.major[i + 1];
            boolean vertical = g.major[i + 2] == sx;
            double world = vertical ? v.toWorldX(sx) : v.toWorldY(sy);
            assertEquals(0, world % 5000, 1e-3);
        }
        for (int i = 0; i < g.minorCount; i += 4) {
            boolean vertical = g.minor[i + 2] == g.minor[i];
            double world = vertical ? v.toWorldX(g.minor[i]) : v.toWorldY(g.minor[i + 1]);
            assertEquals(0, world % 500, 1e-3);
            assertTrue("minor lines exclude the majors", Math.abs(world % 5000) > 1e-3);
        }
    }

    @Test public void gridHandlesNegativeOriginsAndPanning() {
        ViewTransform v = view(0.2, 3000, 2000, 400, 400);          // origin is off-screen to the upper left
        GridLayout g = new GridLayout();
        g.compute(v, 1000, 5000);
        assertTrue(g.minorCount + g.majorCount > 0);
        for (int i = 0; i < g.minorCount; i += 4) {
            boolean vertical = g.minor[i + 2] == g.minor[i];
            double world = vertical ? v.toWorldX(g.minor[i]) : v.toWorldY(g.minor[i + 1]);
            assertEquals(0, ((world % 1000) + 1000) % 1000, 1e-3);
        }
    }

    @Test public void gridNeverProducesAnAbsurdNumberOfLines() {
        GridLayout g = new GridLayout();
        g.compute(view(3.0, 0, 0, 1080, 1920), 10, 100);            // 10 mm grid at maximum zoom-in is fine
        int fine = g.minorCount + g.majorCount;
        g.compute(view(0.004, 0, 0, 1080, 1920), 10, 100);          // 10 mm grid at maximum zoom-out would be ~27,000 lines
        assertEquals("refused instead of drawing a hairball", 0, g.minorCount + g.majorCount);
        assertTrue(fine <= (GridLayout.MAX_LINES_PER_AXIS * 2) * 4);
        g.compute(view(0.1, 0, 0, 0, 0), 500, 5000);                // a view that has not been measured yet
        assertEquals(0, g.minorCount);
    }

    // ---- wall joins and outlines -----------------------------------------------------------------------

    @Test public void cornersAreCappedFreeEndsAndTeesAreNot() {
        List<Wall> ws = new ArrayList<>();
        ws.add(wall(0, 0, 4000, 0));          // 0: meets wall 1 at its end (corner)
        ws.add(wall(4000, 0, 4000, 3000));    // 1: corner at start; free end
        ws.add(wall(2000, 0, 2000, -2000));   // 2: starts in the middle of wall 0 (T): not a joint of ends
        WallJoins j = WallJoins.compute(ws);
        assertFalse(j.capStart[0]);
        assertTrue(j.capEnd[0]);
        assertTrue(j.capStart[1]);
        assertFalse(j.capEnd[1]);
        assertFalse(j.capStart[2]);
        assertFalse(j.capEnd[2]);
    }

    @Test public void aCappedCornerClosesExactlyWithoutAGap() {
        Wall a = wall(0, 0, 4000, 0), b = wall(4000, 0, 4000, 3000);
        double[] ca = WallOutline.corners(a, false, true), cb = WallOutline.corners(b, true, false);
        // wall a's capped end reaches x = 4000 + 100; wall b's capped start reaches y = -100
        double maxX = Math.max(Math.max(ca[2], ca[4]), Math.max(ca[0], ca[6]));
        assertEquals(4100, maxX, 1e-9);
        double minY = Math.min(Math.min(cb[1], cb[3]), Math.min(cb[5], cb[7]));
        assertEquals(-100, minY, 1e-9);
    }

    @Test public void everyWallIsWoundTheSameWayWhateverItsDirection() {
        double[][] dirs = {{4000, 0}, {0, 4000}, {-4000, 0}, {0, -4000}, {3000, 4000}, {-3000, -4000}, {-2500, 1500}};
        double sign = Math.signum(WallOutline.signedArea(WallOutline.corners(wall(0, 0, 4000, 0), false, false)));
        for (double[] d : dirs) {
            double[] c = WallOutline.corners(wall(100, 200, 100 + d[0], 200 + d[1]), true, true);
            double len = Math.hypot(d[0], d[1]);
            double area = WallOutline.signedArea(c);
            assertEquals("same winding", sign, Math.signum(area), 0);
            assertEquals("area = (length + both caps) x thickness", (len + 200) * 200, Math.abs(area), 1e-6);
        }
    }

    // ---- level of detail and text --------------------------------------------------------------------------

    @Test public void detailDropsAsYouZoomOutAndNeverComesBack() {
        double[] scales = {0.004, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0};
        boolean prevFurn = false, prevLabel = false, prevDim = false, prevOpen = false;
        for (double s : scales) {
            Lod lod = new Lod(s, 2.0);
            boolean f = lod.showFurniture(), l = lod.showRoomLabel(3000), d = lod.showDimension(3000), o = lod.showOpeningDetail();
            assertTrue("monotonic furniture", f || !prevFurn);
            assertTrue("monotonic labels", l || !prevLabel);
            assertTrue("monotonic dimensions", d || !prevDim);
            assertTrue("monotonic openings", o || !prevOpen);
            prevFurn = f; prevLabel = l; prevDim = d; prevOpen = o;
        }
        assertFalse(new Lod(0.004, 2.0).showFurniture());
        assertTrue(new Lod(0.5, 2.0).showFurniture());
    }

    @Test public void thresholdsFollowScreenDensityNotPlanUnits() {
        // the same physical size on a denser screen needs proportionally more pixels
        assertTrue(new Lod(0.05, 1.0).showRoomLabel(2000));         // 100 px >= 64
        assertFalse(new Lod(0.05, 2.0).showRoomLabel(2000));        // 100 px <  128
        assertTrue(new Lod(0.1, 2.0).showRoomLabel(2000));          // 200 px >= 128
        assertEquals(3.0f, new Lod(0.1, 2.0).wallMinPx(), 1e-6);
    }

    @Test public void textIsNeverUpsideDown() {
        assertEquals(0, TextPlacement.uprightDeg(0), 1e-9);
        assertEquals(90, TextPlacement.uprightDeg(90), 1e-9);
        assertEquals(-89, TextPlacement.uprightDeg(91), 1e-9);
        assertEquals(0, TextPlacement.uprightDeg(180), 1e-9);
        assertEquals(-45, TextPlacement.uprightDeg(315), 1e-9);
        assertEquals(-45, TextPlacement.uprightDeg(-45), 1e-9);
        for (double a = -720; a <= 720; a += 7.5) {
            double u = TextPlacement.uprightDeg(a);
            assertTrue(a + " -> " + u, u > -90 - 1e-9 && u <= 90 + 1e-9);
            double diff = ((a - u) % 180 + 180) % 180;
            assertTrue("same line direction", diff < 1e-9 || Math.abs(diff - 180) < 1e-9);
        }
    }
}
