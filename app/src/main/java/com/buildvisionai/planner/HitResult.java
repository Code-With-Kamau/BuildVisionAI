package com.buildvisionai.planner;

import com.buildvisionai.models.PlanElement;

public final class HitResult {
    public final PlanElement element;
    public final Handle handle;

    public HitResult(PlanElement element, Handle handle) {
        this.element = element;
        this.handle = handle;
    }

    public boolean isHandle() { return handle != Handle.NONE; }
}
