package com.buildvisionai.estimation;

import com.buildvisionai.models.Project;
import com.buildvisionai.planner.PlanDocument;

/**
 * Turns a drawing plus assumptions plus a rate pack into quantities and a cost, so
 * the estimate screen, the assistant and the report all price a plan the same way. Pure and
 * deterministic; for a large plan call it off the main thread, on a copy of the document.
 */
public final class EstimateRunner {
    private EstimateRunner() {}

    public static final class Result {
        public final QuantitySchedule quantities;
        public final CostEstimate cost;

        Result(QuantitySchedule quantities, CostEstimate cost) {
            this.quantities = quantities;
            this.cost = cost;
        }
    }

    /** The built-in rate pack of the project's county region, before any of the project's own quotes. */
    public static RatePack basePack(Project project) {
        return KenyaRatePack.forRegion(Counties.regionOf(project == null ? null : project.county));
    }

    public static Result run(PlanDocument doc, EstimateSettings settings, RatePack pack) {
        QuantitySchedule q = MaterialEstimator.estimate(doc, settings);
        return new Result(q, CostEstimator.estimate(q, pack, settings));
    }
}
