package com.buildvisionai.domain;

import com.buildvisionai.geometry.DetectedRoom;
import com.buildvisionai.geometry.PlanMetrics;
import com.buildvisionai.geometry.RoomMetrics;
import com.buildvisionai.models.Door;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.Stair;
import com.buildvisionai.models.Window;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.PlanDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Objective drawing checks: things that are wrong or missing in the drawing itself. It does NOT
 * check building regulations or codes (those vary by county and change); it only reports plot
 * coverage figures so the user can compare them with what their approving authority requires.
 */
public final class PlanChecker {
    private PlanChecker() {}

    public enum Severity { INFO, WARNING, ERROR }

    public static final class Issue {
        public final Severity severity;
        public final String code;
        public final String message;
        /** Element to select when the user taps the issue, or null. */
        public final String elementId;
        public final String floorId;

        Issue(Severity severity, String code, String message, String elementId, String floorId) {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.elementId = elementId;
            this.floorId = floorId;
        }
    }

    public static List<Issue> check(PlanDocument doc, Project project) {
        List<Issue> out = new ArrayList<>();
        double groundFootprint = 0, gfa = 0;
        boolean first = true;
        for (Floor f : doc.floors()) {
            FloorPlan fp = doc.floorPlan(f.id);
            if (fp.isEmpty()) continue;
            PlanMetrics pm = fp.metrics();
            double footprint = Math.max(pm.topology.footprintAreaM2, pm.netFloorAreaM2);
            gfa += footprint;
            if (first) { groundFootprint = footprint; first = false; }

            Map<DetectedRoom, RoomMetrics> byShape = new HashMap<>();
            for (RoomMetrics rm : pm.rooms) byShape.put(rm.shape, rm);
            Set<String> inRoom = new HashSet<>();
            for (Room r : fp.rooms) {
                DetectedRoom d = fp.shapeOf(r);
                if (d == null) continue;
                inRoom.addAll(d.wallIds);
                RoomMetrics rm = byShape.get(d);
                if (rm == null) continue;
                if (rm.doorCount == 0) {
                    out.add(new Issue(Severity.WARNING, "ROOM_NO_DOOR",
                            f.name + ": " + r.name + " has no door.", r.id, f.id));
                }
                if (r.roomType.habitable && rm.windowCount == 0) {
                    out.add(new Issue(Severity.WARNING, "HABITABLE_NO_WINDOW",
                            f.name + ": " + r.name + " is a " + r.roomType.label.toLowerCase(Locale.US) + " with no window.", r.id, f.id));
                }
            }
            for (Door d : fp.doors) {
                if (!d.attached) out.add(new Issue(Severity.WARNING, "UNATTACHED_OPENING",
                        f.name + ": a door is on a wall that is missing or too short.", d.id, f.id));
            }
            for (Window w : fp.windows) {
                if (!w.attached) out.add(new Issue(Severity.WARNING, "UNATTACHED_OPENING",
                        f.name + ": a window is on a wall that is missing or too short.", w.id, f.id));
            }
            int loose = 0;
            for (com.buildvisionai.models.Wall w : fp.walls) if (!inRoom.contains(w.id)) loose++;
            if (loose > 0) {
                out.add(new Issue(Severity.INFO, "WALLS_NOT_IN_ROOM",
                        f.name + ": " + loose + " wall(s) are not part of a closed room.", null, f.id));
            }
            if (!fp.stairs.isEmpty() && doc.floors().get(doc.floors().size() - 1).id.equals(f.id)) {
                Stair st = fp.stairs.get(0);
                out.add(new Issue(Severity.INFO, "STAIR_ON_TOP_FLOOR",
                        f.name + ": a staircase is on the top floor; stairs are drawn on the floor they climb from.", st.id, f.id));
            }
        }
        if (project != null && project.plotAreaM2() > 0 && groundFootprint > 0) {
            double plot = project.plotAreaM2();
            double coverage = groundFootprint / plot * 100.0;
            if (groundFootprint > plot) {
                out.add(new Issue(Severity.ERROR, "FOOTPRINT_EXCEEDS_PLOT", String.format(Locale.US,
                        "The ground-floor footprint (%.1f m²) is larger than the plot (%.1f m²).", groundFootprint, plot), null, null));
            } else {
                out.add(new Issue(Severity.INFO, "PLOT_COVERAGE", String.format(Locale.US,
                        "Plot coverage %.0f%% (footprint %.1f m² on %.1f m²). Plot ratio %.2f. Compare with your county's limits.",
                        coverage, groundFootprint, plot, gfa / plot), null, null));
            }
        }
        return out;
    }

    public static int count(List<Issue> issues, Severity s) {
        int n = 0;
        for (Issue i : issues) if (i.severity == s) n++;
        return n;
    }
}
