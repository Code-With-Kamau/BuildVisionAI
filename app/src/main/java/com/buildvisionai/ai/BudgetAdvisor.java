package com.buildvisionai.ai;

import com.buildvisionai.collaboration.ChangeProposal;
import com.buildvisionai.collaboration.ProposalOp;
import com.buildvisionai.estimation.CostEstimate;
import com.buildvisionai.estimation.CostEstimator;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.FinishLevel;
import com.buildvisionai.estimation.MaterialEstimator;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.estimation.RoofType;
import com.buildvisionai.models.SuggestionSource;
import com.buildvisionai.planner.PlanDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Finds ways to bring an estimate within budget. Deterministic: every saving is computed by
 * re-running the real estimator on a changed setting, so the numbers are exact for the model, not
 * guessed. An AI service may be used afterwards only to phrase the result; it cannot change a number.
 */
public final class BudgetAdvisor {
    private BudgetAdvisor() {}

    public enum Status { NO_BUDGET, UNDER, TIGHT, OVER }

    public static final class Option {
        public final String title;
        public final String tradeOff;
        /** Average-total saving in KES (positive = cheaper). */
        public final double savingKes;
        public final ChangeProposal proposal;
        private final Consumer<EstimateSettings> mutation;

        Option(String title, String tradeOff, double savingKes, ChangeProposal proposal, Consumer<EstimateSettings> mutation) {
            this.title = title;
            this.tradeOff = tradeOff;
            this.savingKes = savingKes;
            this.proposal = proposal;
            this.mutation = mutation;
        }
    }

    public static final class Report {
        public Status status = Status.NO_BUDGET;
        public double budgetKes;
        public double totalKes;
        /** Positive = over budget. */
        public double gapKes;
        public final List<Option> options = new ArrayList<>();
        /** How many of the biggest options, applied together, bring the estimate within budget (0 if never). */
        public int optionsNeeded;
        public double totalAfterNeeded;
        public String summary;
    }

    public static Report advise(PlanDocument doc, EstimateSettings base, RatePack pack, double budgetKes) {
        Report r = new Report();
        r.budgetKes = budgetKes;
        double total = total(doc, base, pack);
        r.totalKes = total;
        if (budgetKes <= 0) {
            r.summary = String.format(Locale.US, "Estimated KES %,.0f. Set a budget to get savings suggestions.", total);
            return r;
        }
        r.gapKes = total - budgetKes;
        r.status = r.gapKes > 0 ? Status.OVER : (r.gapKes > -0.05 * budgetKes ? Status.TIGHT : Status.UNDER);

        if (base.finishLevel == FinishLevel.PREMIUM) {
            candidate(r, doc, base, pack, "Use standard finishes", "Less expensive tiles, paint, fixtures, doors and windows.",
                    ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, FinishLevel.STANDARD.name(), 0), s -> s.finishLevel = FinishLevel.STANDARD);
        } else if (base.finishLevel == FinishLevel.STANDARD) {
            candidate(r, doc, base, pack, "Use basic finishes", "Cheaper tiles, paint, fixtures, doors and windows; plainer look and shorter life.",
                    ProposalOp.setting(ProposalOp.Type.SET_FINISH_LEVEL, FinishLevel.BASIC.name(), 0), s -> s.finishLevel = FinishLevel.BASIC);
        }
        if (base.roofType == RoofType.GABLED_TILE || base.roofType == RoofType.HIPPED_TILE) {
            candidate(r, doc, base, pack, "Cover the roof with iron sheets", "Lighter and cheaper; different look and more noise in rain.",
                    ProposalOp.setting(ProposalOp.Type.SET_ROOF_TYPE, RoofType.GABLED_IRON_SHEET.name(), 0), s -> s.roofType = RoofType.GABLED_IRON_SHEET);
        } else if (base.roofType == RoofType.HIPPED_IRON_SHEET) {
            candidate(r, doc, base, pack, "Use a gabled roof instead of hipped", "Simpler roof; gable ends need masonry and the shape changes.",
                    ProposalOp.setting(ProposalOp.Type.SET_ROOF_TYPE, RoofType.GABLED_IRON_SHEET.name(), 0), s -> s.roofType = RoofType.GABLED_IRON_SHEET);
        } else if (base.roofType == RoofType.FLAT_SLAB) {
            candidate(r, doc, base, pack, "Use a pitched iron-sheet roof instead of a concrete slab", "Cheaper and easier to repair; needs a roof structure and loses the flat usable roof.",
                    ProposalOp.setting(ProposalOp.Type.SET_ROOF_TYPE, RoofType.GABLED_IRON_SHEET.name(), 0), s -> s.roofType = RoofType.GABLED_IRON_SHEET);
        }
        if (base.ceilings) {
            candidate(r, doc, base, pack, "Leave out gypsum ceilings", "Exposed roof structure: hotter rooms, more noise and dust.",
                    ProposalOp.setting(ProposalOp.Type.SET_CEILINGS, "false", 0), s -> s.ceilings = false);
        }
        if (base.windowGrills) {
            candidate(r, doc, base, pack, "Leave out window grills", "Lower security. Consider lockable shutters or fitting them later.",
                    ProposalOp.setting(ProposalOp.Type.SET_WINDOW_GRILLS, "false", 0), s -> s.windowGrills = false);
        }
        if (base.preliminariesPct > 0 || base.overheadProfitPct > 0) {
            double saving = total - total(doc, withSettings(base, s -> { s.preliminariesPct = 0; s.overheadProfitPct = 0; }), pack);
            if (saving > 0) {
                ChangeProposal p = new ChangeProposal("Manage the build yourself", "Removes contractor preliminaries and overhead & profit.", SuggestionSource.AI);
                p.add(ProposalOp.note("Hire fundis directly and supervise the site. Saves contractor margin but costs you time and needs experience or a supervising professional."));
                p.estimatedDeltaKes = -saving;
                r.options.add(new Option("Manage the build yourself",
                        "Needs your time and site supervision; you carry the risk the contractor would carry.", saving, p,
                        s -> { s.preliminariesPct = 0; s.overheadProfitPct = 0; }));
            }
        }
        Collections.sort(r.options, (a, b) -> Double.compare(b.savingKes, a.savingKes));

        // greedy: apply the biggest savings together, re-estimating each time (savings are not additive)
        if (r.gapKes > 0) {
            EstimateSettings cur = base.copy();
            double t = total;
            int n = 0;
            for (Option o : r.options) {
                if (o.savingKes <= 0) continue;
                o.mutation.accept(cur);
                t = total(doc, cur, pack);
                n++;
                if (t <= budgetKes) { r.optionsNeeded = n; r.totalAfterNeeded = t; break; }
            }
        }
        r.summary = summary(r);
        return r;
    }

    private static String summary(Report r) {
        switch (r.status) {
            case OVER: {
                String s = String.format(Locale.US, "Estimated KES %,.0f is KES %,.0f (%.0f%%) over your KES %,.0f budget.",
                        r.totalKes, r.gapKes, 100 * r.gapKes / r.budgetKes, r.budgetKes);
                if (r.optionsNeeded > 0) {
                    s += String.format(Locale.US, " The %d biggest saving%s below, applied together, bring it to KES %,.0f.",
                            r.optionsNeeded, r.optionsNeeded == 1 ? "" : "s", r.totalAfterNeeded);
                } else if (!r.options.isEmpty()) {
                    s += " The options below help but do not close the gap on their own; consider reducing the built area.";
                } else {
                    s += " Reducing the built area is the remaining lever.";
                }
                return s;
            }
            case TIGHT:
                return String.format(Locale.US, "Estimated KES %,.0f is within budget but only %.0f%% below it. Prices move, so keep the contingency.",
                        r.totalKes, 100 * -r.gapKes / r.budgetKes);
            default:
                return String.format(Locale.US, "Estimated KES %,.0f is KES %,.0f under your KES %,.0f budget.", r.totalKes, -r.gapKes, r.budgetKes);
        }
    }

    private static void candidate(Report r, PlanDocument doc, EstimateSettings base, RatePack pack, String title,
                                  String tradeOff, ProposalOp op, Consumer<EstimateSettings> mutation) {
        double saving = r.totalKes - total(doc, withSettings(base, mutation), pack);
        if (saving <= 0) return;
        ChangeProposal p = new ChangeProposal(title, tradeOff, SuggestionSource.AI);
        p.add(op);
        p.estimatedDeltaKes = -saving;
        r.options.add(new Option(title, tradeOff, saving, p, mutation));
    }

    private static EstimateSettings withSettings(EstimateSettings base, Consumer<EstimateSettings> m) {
        EstimateSettings s = base.copy();
        m.accept(s);
        return s;
    }

    static double total(PlanDocument doc, EstimateSettings s, RatePack pack) {
        CostEstimate e = CostEstimator.estimate(MaterialEstimator.estimate(doc, s), pack, s);
        return e.totalAvg;
    }
}
