package com.buildvisionai.estimation;

/** A percentage add-on to the construction subtotal (contingency, fees, levies). */
public final class StatutoryItem {
    public final String code;
    public final String label;
    public final double ratePct;
    public final double min, avg, max;
    public final String note;

    StatutoryItem(String code, String label, double ratePct, double baseMin, double baseAvg, double baseMax, String note) {
        this.code = code;
        this.label = label;
        this.ratePct = ratePct;
        this.min = baseMin * ratePct / 100.0;
        this.avg = baseAvg * ratePct / 100.0;
        this.max = baseMax * ratePct / 100.0;
        this.note = note;
    }
}
