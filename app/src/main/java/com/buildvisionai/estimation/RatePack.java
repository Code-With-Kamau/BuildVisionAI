package com.buildvisionai.estimation;

import java.util.EnumMap;
import java.util.Map;

/**
 * A set of unit prices for one region and date. Prices are KES per {@link Item#unit}, treated as
 * VAT-inclusive, supply-only for materials and installation-only for labour. Users can override any
 * rate with a local quote; overrides travel with the pack so an estimate stays reproducible.
 */
public final class RatePack {
    public final String id;
    public final String name;
    public final Region region;
    /** When the prices were last checked, e.g. "2026-10". */
    public final String asOf;
    /** Where the numbers came from and how far to trust them. */
    public final String disclaimer;
    private final EnumMap<Item, Rate> rates = new EnumMap<>(Item.class);
    private final EnumMap<FinishLevel, Double> finishFactors = new EnumMap<>(FinishLevel.class);

    public RatePack(String id, String name, Region region, String asOf, String disclaimer) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.asOf = asOf;
        this.disclaimer = disclaimer;
        finishFactors.put(FinishLevel.BASIC, 0.70);
        finishFactors.put(FinishLevel.STANDARD, 1.00);
        finishFactors.put(FinishLevel.PREMIUM, 1.80);
    }

    public RatePack set(Item item, Rate r) {
        rates.put(item, r);
        return this;
    }

    public boolean has(Item item) { return rates.containsKey(item); }

    /** Base rate for the item (before finish level), or null if the pack has none. */
    public Rate base(Item item) { return rates.get(item); }

    public double finishFactor(FinishLevel level) { return finishFactors.get(level); }

    public void setFinishFactor(FinishLevel level, double factor) { finishFactors.put(level, factor); }

    /** Rate to charge: base, scaled by the finish level for finish-sensitive items. */
    public Rate effective(Item item, FinishLevel level) {
        Rate r = rates.get(item);
        if (r == null) return null;
        return item.finishSensitive ? r.times(finishFactor(level)) : r;
    }

    public Map<Item, Rate> all() { return rates; }

    public RatePack copyAs(String newId, String newName) {
        RatePack p = new RatePack(newId, newName, region, asOf, disclaimer);
        p.rates.putAll(rates);
        p.finishFactors.putAll(finishFactors);
        return p;
    }
}
