package com.placify.analytics.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic past-season data so the model works on day one, before
 * the college has recorded real seasons. Placement depends on CGPA, backlogs
 * and preparation through a known logistic relationship plus noise, which the
 * model then has to learn. Deterministic (fixed seed).
 */
public final class SyntheticHistory {

    private SyntheticHistory() {
    }

    public record Row(Features features, boolean placed) {
    }

    public static List<Row> generate(int size, long seed) {
        Random r = new Random(seed);
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double skill = r.nextGaussian();
            double cgpa = clamp(7.4 + 0.9 * r.nextGaussian() + 0.25 * skill, 5.0, 10.0);
            int backlogs = r.nextDouble() < 0.75 ? 0 : 1 + r.nextInt(4);
            double coding = clamp(45 + 20 * skill + 12 * r.nextGaussian(), 0, 100);
            double aptitude = clamp(55 + 9 * skill + 15 * r.nextGaussian(), 0, 100);
            double ats = clamp(55 + 5 * skill + 15 * r.nextGaussian(), 0, 100);
            double mock = r.nextDouble() < 0.3 ? 0 : clamp(50 + 12 * skill + 15 * r.nextGaussian(), 0, 100);
            double contests = r.nextDouble() < 0.4 ? 0 : clamp(30 + 15 * skill + 20 * r.nextGaussian(), 0, 100);

            double logit = trueLogit(cgpa, backlogs, coding, aptitude, ats, mock, contests);
            boolean placed = r.nextDouble() < 1 / (1 + Math.exp(-logit));
            rows.add(new Row(new Features(round(cgpa), backlogs, round(coding), round(aptitude), round(ats),
                    round(mock), round(contests)), placed));
        }
        return rows;
    }

    /** The relationship hidden in the synthetic data; also the fallback when no model is trained. */
    public static double trueLogit(double cgpa, int backlogs, double coding, double aptitude, double ats, double mock,
                                   double contests) {
        return -9.0 + 0.55 * cgpa - 0.6 * Math.min(5, backlogs) + 0.035 * coding + 0.025 * aptitude + 0.015 * ats
                + 0.02 * mock + 0.01 * contests;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
