package com.buildvisionai.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.estimation.TestPlansAccess;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Wall;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

import java.util.List;

public class DomainTest {

    static boolean has(List<PlanChecker.Issue> l, String code) {
        for (PlanChecker.Issue i : l) if (i.code.equals(code)) return true;
        return false;
    }

    @Test public void cleanReferenceHouseHasNoWarningsExceptNoWindowRooms() {
        PlanDocument d = TestPlansAccess.bungalow();
        List<PlanChecker.Issue> issues = PlanChecker.check(d, null);
        // the reference plan has no window in the dining/kitchen? it has one on the left wall; toilet and bath have casements
        assertEquals(issues.toString(), 0, PlanChecker.count(issues, PlanChecker.Severity.ERROR));
        for (PlanChecker.Issue i : issues) assertFalse(i.message, i.code.equals("UNATTACHED_OPENING"));
    }

    @Test public void roomWithoutDoorOrWindowIsReported() {
        PlanDocument d = TestPlansAccess.bungalow();
        for (com.buildvisionai.models.PlanElement e : new java.util.ArrayList<>(d.all())) {
            if (e instanceof Door) d.remove(e.id);
        }
        List<PlanChecker.Issue> issues = PlanChecker.check(d, null);
        int noDoor = 0;
        for (PlanChecker.Issue i : issues) if (i.code.equals("ROOM_NO_DOOR")) noDoor++;
        assertEquals(7, noDoor);
        for (PlanChecker.Issue i : issues) if (i.code.equals("ROOM_NO_DOOR")) assertTrue(d.get(i.elementId) instanceof Room);
    }

    @Test public void openingOnAWallThatShrankIsReported() {
        PlanDocument d = TestPlansAccess.bungalow();
        Door door = null;
        for (com.buildvisionai.models.PlanElement e : d.all()) if (e instanceof Door) { door = (Door) e; break; }
        Wall w = (Wall) d.get(door.wallId);
        Wall shortened = w.copy();
        shortened.endX = shortened.startX + 300; shortened.endY = shortened.startY;
        d.put(shortened);
        assertTrue(has(PlanChecker.check(d, null), "UNATTACHED_OPENING"));
    }

    @Test public void plotCoverageIsReportedAndOversizeBuildingIsAnError() {
        PlanDocument d = TestPlansAccess.bungalow();
        Project p = new Project();
        p.plotWidthM = 15.24; p.plotLengthM = 30.48;
        List<PlanChecker.Issue> ok = PlanChecker.check(d, p);
        assertTrue(has(ok, "PLOT_COVERAGE"));
        for (PlanChecker.Issue i : ok) if (i.code.equals("PLOT_COVERAGE")) assertTrue(i.message, i.message.contains("27%"));   // 124.4 / 464.5
        p.plotWidthM = 10; p.plotLengthM = 10;
        assertTrue(has(PlanChecker.check(d, p), "FOOTPRINT_EXCEEDS_PLOT"));
    }

    @Test public void diffShowsMovedWallAsModifiedAndNewRoomAsAdded() {
        PlanDocument a = TestPlansAccess.bungalow();
        PlanDocument b = a.copy();
        assertTrue(VersionDiff.compare(a, b).isEmpty());

        Wall w = null;
        for (com.buildvisionai.models.PlanElement e : b.all()) if (e instanceof Wall) { w = (Wall) e; break; }
        Wall moved = w.copy();
        moved.thicknessMm = 225;
        b.put(moved);
        b.put(new Wall("new1", w.floorId, 0, 20000, 3000, 20000, com.buildvisionai.models.WallKind.INTERNAL));
        b.remove(firstDoorId(b));

        VersionDiff.Result r = VersionDiff.compare(a, b);
        assertEquals(1, r.count(VersionDiff.Change.ADDED));
        assertEquals(1, r.count(VersionDiff.Change.REMOVED));
        assertEquals(1, r.count(VersionDiff.Change.MODIFIED));
        boolean found = false;
        for (VersionDiff.Entry e : r.entries) {
            if (e.change == VersionDiff.Change.MODIFIED) {
                found = true;
                assertEquals(w.id, e.elementId);
                assertTrue(e.details.toString(), e.details.get(0).contains("thickness (mm): 200 -> 225"));
            }
        }
        assertTrue(found);
        assertEquals("1 added, 1 removed, 1 modified", r.summary());
    }

    @Test public void derivedRoomMetricsAreNotCountedAsEdits() {
        PlanDocument a = TestPlansAccess.bungalow();
        PlanDocument b = a.copy();
        for (Room room : b.floorPlan("g").rooms) {
            Room c = room.copy();
            c.areaM2 += 5;       // derived cache differs, user data does not
            b.put(c);
        }
        assertTrue(VersionDiff.compare(a, b).isEmpty());
    }

    private static String firstDoorId(PlanDocument d) {
        for (com.buildvisionai.models.PlanElement e : d.all()) if (e instanceof Door) return e.id;
        return "none";
    }
}
