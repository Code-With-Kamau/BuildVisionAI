package com.buildvisionai.ai;

import com.buildvisionai.estimation.CostCategory;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.Room;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.PlanDocument;

import java.util.Locale;

/**
 * Builds the text an assistant sees. Facts about the building only: no owner name, phone, email or
 * exact location.
 */
public final class PlanSummarizer {
    private PlanSummarizer() {}

    public static String summarize(Project project, PlanDocument doc, EstimateSettings s, CostEstimate cost) {
        StringBuilder b = new StringBuilder();
        b.append("Building type: ").append(project.buildingType == null ? "unspecified" : project.buildingType.label).append('\n');
        if (project.county != null) b.append("County: ").append(project.county).append('\n');
        if (project.plotAreaM2() > 0) b.append(String.format(Locale.US, "Plot: %.1f x %.1f m (%.0f m2)%n", project.plotWidthM, project.plotLengthM, project.plotAreaM2()));
        if (project.budgetKes > 0) b.append(String.format(Locale.US, "Budget: KES %,.0f%n", project.budgetKes));
        b.append("Finish level: ").append(s.finishLevel.label).append(", roof: ").append(s.roofType.label).append('\n');
        for (Floor f : doc.floors()) {
            FloorPlan fp = doc.floorPlan(f.id);
            if (fp.isEmpty()) continue;
            b.append(f.name).append(": ").append(fp.walls.size()).append(" walls, ")
                    .append(fp.doors.size()).append(" doors, ").append(fp.windows.size()).append(" windows\n");
            for (Room r : fp.rooms) {
                b.append(String.format(Locale.US, "  - %s (%s) %.1f m2%n", r.name, r.roomType.label, r.areaM2));
            }
        }
        if (cost != null && cost.totalAvg > 0) {
            b.append(String.format(Locale.US, "Estimated cost: KES %,.0f (range %,.0f - %,.0f), %,.0f per m2%n",
                    cost.totalAvg, cost.totalMin, cost.totalMax, cost.perM2Avg));
            for (CostCategory c : CostCategory.values()) {
                b.append(String.format(Locale.US, "  %s: KES %,.0f%n", c.label, cost.category(c).avg));
            }
        }
        return b.toString();
    }
}
