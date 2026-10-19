package com.buildvisionai.estimation;

public final class CategoryCost {
    public final CostCategory category;
    public double min, avg, max;

    CategoryCost(CostCategory category) { this.category = category; }
}
