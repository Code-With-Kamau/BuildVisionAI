package com.buildvisionai.estimation;

/** Unit price band in KES: low, typical and high. */
public final class Rate {
    public final double min, avg, max;

    public Rate(double min, double avg, double max) {
        this.min = min;
        this.avg = avg;
        this.max = max;
    }

    /** Band of -12% / +15% around a typical price. */
    public static Rate around(double avg) { return new Rate(avg * 0.88, avg, avg * 1.15); }

    public Rate times(double k) { return new Rate(min * k, avg * k, max * k); }
}
