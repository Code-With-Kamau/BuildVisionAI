package com.buildvisionai.reports.data;

import com.buildvisionai.utils.Strings;
import com.buildvisionai.domain.PlanChecker;
import com.buildvisionai.estimation.CategoryCost;
import com.buildvisionai.estimation.CostCategory;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.MaterialLine;
import com.buildvisionai.estimation.QuantitySchedule;
import com.buildvisionai.estimation.StatutoryItem;
import com.buildvisionai.geometry.PlanMetrics;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.Floor;
import com.buildvisionai.models.Project;
import com.buildvisionai.models.ProjectVersion;
import com.buildvisionai.models.Room;
import com.buildvisionai.models.WorkflowState;
import com.buildvisionai.planner.FloorPlan;
import com.buildvisionai.planner.PlanDocument;
import com.buildvisionai.utils.LengthFormatter;
import com.buildvisionai.utils.MoneyFormatter;

import java.util.List;
import java.util.Locale;

/** Assembles a {@link ReportModel} from a version's plan, estimate and approval history. */
public final class ReportBuilder {
    private ReportBuilder() {}

    public static final String DISCLAIMER =
            "This is a planning estimate produced from the drawing and editable assumptions. It is not a bill of quantities, "
                    + "a structural design or an approval. Have a registered architect, engineer and quantity surveyor confirm "
                    + "sizes, specifications and prices before building. AI suggestions in BuildVision AI are proposals only "
                    + "and never change a design without a person's approval.";

    public static ReportModel build(Project project, ProjectVersion version, PlanDocument doc, QuantitySchedule q,
                                    CostEstimate cost, List<Approval> approvals, List<PlanChecker.Issue> issues,
                                    String generatedAt) {
        ReportModel m = new ReportModel();
        m.title = "Design & Cost Report";
        m.projectName = project.projectName;
        m.owner = project.ownerName;
        m.location = project.location;
        m.county = project.county;
        m.buildingType = project.buildingType == null ? "" : project.buildingType.label;
        m.plotText = project.plotAreaM2() > 0
                ? String.format(Locale.US, "%.1f x %.1f m (%.0f m²)", project.plotWidthM, project.plotLengthM, project.plotAreaM2())
                : "Not set";
        m.budgetText = project.budgetKes > 0 ? MoneyFormatter.kes(project.budgetKes) : "Not set";
        m.versionText = "Version " + version.number + (version.label == null ? "" : " - " + version.label);
        m.statusText = statusText(version.workflowState);
        m.generatedAt = generatedAt;
        m.watermark = (version.workflowState == WorkflowState.APPROVED || version.workflowState == WorkflowState.LOCKED)
                ? null : "DRAFT - NOT APPROVED";
        m.disclaimer = DISCLAIMER;

        for (Floor f : doc.floors()) {
            FloorPlan fp = doc.floorPlan(f.id);
            if (fp.isEmpty()) continue;
            PlanMetrics pm = fp.metrics();
            ReportModel.FloorSection s = new ReportModel.FloorSection();
            s.name = f.name;
            s.grossAreaText = String.format(Locale.US, "%.1f m²", Math.max(pm.topology.footprintAreaM2, pm.netFloorAreaM2));
            s.netAreaText = String.format(Locale.US, "%.1f m²", pm.netFloorAreaM2);
            s.countsText = fp.walls.size() + " walls, " + pm.doorCount + " doors, " + pm.windowCount + " windows";
            for (Room r : fp.rooms) {
                if (fp.shapeOf(r) == null) continue;
                ReportModel.RoomRow row = new ReportModel.RoomRow();
                row.name = r.name;
                row.type = r.roomType.label;
                row.area = String.format(Locale.US, "%.2f m²", r.areaM2);
                row.size = String.format(Locale.US, "%s x %s", LengthFormatter.metric(r.widthM * 1000), LengthFormatter.metric(r.lengthM * 1000));
                s.rooms.add(row);
            }
            m.floors.add(s);
        }

        for (MaterialLine l : q.lines) {
            ReportModel.QuantityRow r = new ReportModel.QuantityRow();
            r.category = l.category.label;
            r.description = l.item.description;
            r.unit = l.item.unit;
            r.quantity = formatQty(l.qty);
            r.allowance = l.allowance;
            r.note = Strings.join("; ", l.basis());
            m.quantities.add(r);
        }

        for (CostCategory c : CostCategory.values()) {
            CategoryCost cc = cost.category(c);
            m.costRows.add(row(c.label, cc.min, cc.avg, cc.max, cost.subtotalAvg > 0 ? 100 * cc.avg / cost.subtotalAvg : 0));
        }
        for (StatutoryItem a : cost.addOns) {
            m.addOnRows.add(row(a.label + String.format(Locale.US, " (%.1f%%)", a.ratePct), a.min, a.avg, a.max, 0));
        }
        m.total = row("Total", cost.totalMin, cost.totalAvg, cost.totalMax, 0);
        m.perM2Text = cost.grossFloorAreaM2 > 0 ? String.format(Locale.US, "%s per m² (gross floor area %.1f m²)",
                MoneyFormatter.kes(cost.perM2Avg), cost.grossFloorAreaM2) : "";
        m.vatText = "Rates include 16% VAT; about " + MoneyFormatter.kes(cost.vatIncludedAvg) + " of the typical total is VAT.";
        m.benchmarkText = cost.benchmark == null ? "" : cost.benchmark.message;
        m.ratePackText = cost.ratePackId + " (" + cost.region.label + ", prices as of " + cost.ratePackAsOf + "). " + cost.disclaimer;
        m.assumptions.addAll(q.notes);
        m.warnings.addAll(cost.warnings);
        if (issues != null) {
            for (PlanChecker.Issue i : issues) if (i.severity != PlanChecker.Severity.INFO) m.issues.add(i.message);
        }
        if (approvals != null) {
            for (Approval a : approvals) {
                ReportModel.SignOff s = new ReportModel.SignOff();
                s.who = a.userName;
                s.role = a.role == null ? "" : a.role.label;
                s.stage = a.stage == null ? "" : statusText(a.stage);
                s.decision = a.decision == null ? "" : (a.decision == com.buildvisionai.models.ApprovalDecision.APPROVED ? "Approved" : "Changes requested");
                s.comment = a.comment == null ? "" : a.comment;
                s.date = formatDate(a.createdAt);
                m.signOffs.add(s);
            }
        }
        return m;
    }

    /** "11 Oct 2026 13:05" in East Africa Time (UTC+3, no daylight saving), the zone of every user of this app. */
    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0) return "";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMM yyyy HH:mm", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("Africa/Nairobi"));
        return f.format(new java.util.Date(epochMillis));
    }

    public static String statusText(WorkflowState s) {
        switch (s) {
            case DRAFT: return "Draft";
            case PROFESSIONAL_REVIEW: return "Professional review";
            case OWNER_REVIEW: return "Owner review";
            case APPROVED: return "Approved";
            default: return "Locked";
        }
    }

    private static ReportModel.CostRow row(String label, double min, double avg, double max, double sharePct) {
        ReportModel.CostRow r = new ReportModel.CostRow();
        r.label = label;
        r.min = MoneyFormatter.kes(min);
        r.avg = MoneyFormatter.kes(avg);
        r.max = MoneyFormatter.kes(max);
        r.share = sharePct > 0 ? String.format(Locale.US, "%.0f%%", sharePct) : "";
        return r;
    }

    /** "1,240" for whole numbers and large values, "12.50" for small fractional ones. */
    public static String formatQty(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.format(Locale.US, "%,.0f", v);
        return v >= 100 ? String.format(Locale.US, "%,.0f", v) : String.format(Locale.US, "%,.2f", v);
    }
}
