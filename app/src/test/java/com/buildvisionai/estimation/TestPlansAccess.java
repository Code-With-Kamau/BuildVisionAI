package com.buildvisionai.estimation;

import com.buildvisionai.planner.PlanDocument;

/** Lets tests in other packages use the package-private reference plans. */
public final class TestPlansAccess {
    private TestPlansAccess() {}

    public static PlanDocument bungalow() { return TestPlans.bungalow(); }
}
