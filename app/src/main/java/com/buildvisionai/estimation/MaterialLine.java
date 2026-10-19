package com.buildvisionai.estimation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One line of the quantity schedule: an item in a cost category with its net and bought quantity. */
public final class MaterialLine {
    public final CostCategory category;
    public final Item item;
    /** Quantity measured from the drawing. */
    public double netQty;
    /** Quantity to buy: net plus wastage, rounded up for whole-piece items. */
    public double qty;
    /** True when the quantity is an allowance rather than a measurement. */
    public boolean allowance;
    private final List<String> basis = new ArrayList<>();

    MaterialLine(CostCategory category, Item item) {
        this.category = category;
        this.item = item;
    }

    void addBasis(String text) {
        if (text != null && !basis.contains(text)) basis.add(text);
    }

    /** How the quantity was derived. */
    public List<String> basis() { return Collections.unmodifiableList(basis); }

    public double wastePct() { return netQty > 1e-12 ? (qty / netQty - 1) * 100.0 : 0; }
}
