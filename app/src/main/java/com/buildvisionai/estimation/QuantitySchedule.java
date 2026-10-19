package com.buildvisionai.estimation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Result of the material engine: what to buy, how much work there is, and what was assumed. */
public final class QuantitySchedule {
    public final List<MaterialLine> lines = new ArrayList<>();
    public final EnumMap<WorkItem, Double> work = new EnumMap<>(WorkItem.class);
    /** Assumptions the user should know about (allowances, simplifications). */
    public final List<String> notes = new ArrayList<>();
    /** Problems that make the numbers incomplete (unattached openings, floors without rooms ...). */
    public final List<String> warnings = new ArrayList<>();
    public int floors;
    /** Sum over floors of the outer footprint area including walls (m2). */
    public double grossFloorAreaM2;
    /** Sum over floors of enclosed room areas (m2). */
    public double netFloorAreaM2;

    public double work(WorkItem w) {
        Double v = work.get(w);
        return v == null ? 0 : v;
    }

    /** Total bought quantity of an item across all categories. */
    public double total(Item item) {
        double t = 0;
        for (MaterialLine l : lines) if (l.item == item) t += l.qty;
        return t;
    }

    public double total(CostCategory c, Item item) {
        double t = 0;
        for (MaterialLine l : lines) if (l.category == c && l.item == item) t += l.qty;
        return t;
    }

    public MaterialLine find(CostCategory c, Item item) {
        for (MaterialLine l : lines) if (l.category == c && l.item == item) return l;
        return null;
    }

    public Map<WorkItem, Double> workMap() { return work; }
}
