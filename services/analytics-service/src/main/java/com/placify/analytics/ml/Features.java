package com.placify.analytics.ml;

/**
 * Model input: academic record plus preparation level per area, each area as
 * a percentage of its maximum (0..100), CGPA on a 10-point scale.
 */
public record Features(double cgpa, int backlogs, double coding, double aptitude, double ats, double mock, double contests) {

    static final String[] NAMES = {"cgpa", "backlogs", "coding", "aptitude", "ats", "mock", "contests"};

    /** Scaled to 0..1 so SGD converges and weights are comparable. */
    double[] vector() {
        return new double[] {
                clamp(cgpa / 10.0),
                Math.min(5, Math.max(0, backlogs)) / 5.0,
                clamp(coding / 100.0),
                clamp(aptitude / 100.0),
                clamp(ats / 100.0),
                clamp(mock / 100.0),
                clamp(contests / 100.0)
        };
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
