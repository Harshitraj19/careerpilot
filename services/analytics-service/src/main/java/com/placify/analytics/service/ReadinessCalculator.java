package com.placify.analytics.service;

import com.placify.analytics.client.AnalyticsClients.AssessmentStats;
import com.placify.analytics.client.AnalyticsClients.CodingStats;
import com.placify.analytics.client.AnalyticsClients.MockStats;

import java.util.ArrayList;
import java.util.List;

/**
 * Placement Readiness Score (0-100), a single number that tells a student how
 * prepared they are. Pure function so it is easy to test and explain:
 * <pre>
 *   coding practice   30   (problems solved weighted by difficulty + rating)
 *   aptitude          20   (practice accuracy and volume + test average)
 *   resume ATS        15   (best ATS score)
 *   profile           10   (profile completeness)
 *   mock interviews   15   (average AI mock-interview score, needs 3 interviews for full credit)
 *   contests          10   (participation + average percentile)
 * </pre>
 */
public final class ReadinessCalculator {

    public static final int CODING_MAX = 30;
    public static final int APTITUDE_MAX = 20;
    public static final int ATS_MAX = 15;
    public static final int PROFILE_MAX = 10;
    public static final int MOCK_MAX = 15;
    public static final int CONTESTS_MAX = 10;

    private ReadinessCalculator() {
    }

    public record Components(double coding, double aptitude, double ats, double profile, double mock, double contests) {

        public int total() {
            return (int) Math.round(Math.min(100, coding + aptitude + ats + profile + mock + contests));
        }
    }

    public record Suggestion(String area, String text, int potentialGain) {
    }

    public static Components compute(CodingStats coding, AssessmentStats aptitude, Integer bestAts, int completeness,
                                     MockStats mock) {
        return new Components(coding(coding), aptitude(aptitude), ats(bestAts), profile(completeness), mock(mock),
                contests(coding));
    }

    static double coding(CodingStats c) {
        if (c == null) {
            return 0;
        }
        // 150 "difficulty points" (e.g. 40 easy + 35 medium + 13 hard) give full volume credit
        double points = c.easySolved() + 2.0 * c.mediumSolved() + 3.0 * c.hardSolved();
        double volume = Math.min(1, points / 150.0) * 20;
        // rating 1200 -> 0, 2000+ -> full
        double rating = clamp((c.rating() - 1200) / 800.0) * 10;
        return round(volume + rating);
    }

    static double aptitude(AssessmentStats a) {
        if (a == null) {
            return 0;
        }
        int attempted = a.practice() == null ? 0 : a.practice().stream().mapToInt(t -> t.attempted()).sum();
        double volumeFactor = Math.min(1, attempted / 150.0);
        double practice = clamp(a.practiceAccuracy() / 100.0) * (0.4 + 0.6 * volumeFactor);
        if (a.averageTestPercent() == null || a.testsTaken() == 0) {
            return round(practice * APTITUDE_MAX);
        }
        double tests = clamp(a.averageTestPercent() / 100.0);
        return round((0.5 * practice + 0.5 * tests) * APTITUDE_MAX);
    }

    static double ats(Integer best) {
        return best == null ? 0 : round(clamp(best / 100.0) * ATS_MAX);
    }

    static double profile(int completeness) {
        return round(clamp(completeness / 100.0) * PROFILE_MAX);
    }

    static double mock(MockStats m) {
        if (m == null || m.completed() == 0 || m.averageScore() == null) {
            return 0;
        }
        double confidence = Math.min(1, m.completed() / 3.0);
        return round(clamp(m.averageScore() / 100.0) * confidence * MOCK_MAX);
    }

    static double contests(CodingStats c) {
        if (c == null || c.contestsJoined() == 0) {
            return 0;
        }
        double participation = Math.min(1, c.contestsJoined() / 5.0) * 4;
        double percentile = c.averageContestPercentile() == null ? 0 : clamp(c.averageContestPercentile() / 100.0) * 6;
        return round(participation + percentile);
    }

    /** Concrete next steps, biggest possible gain first. */
    public static List<Suggestion> suggestions(Components c) {
        List<Suggestion> out = new ArrayList<>();
        add(out, "Coding", c.coding(), CODING_MAX,
                "Solve more medium and hard problems in Coding Practice; the recommended list adapts to your rating.");
        add(out, "Aptitude", c.aptitude(), APTITUDE_MAX,
                "Practise quantitative, logical and verbal MCQs daily and take mock aptitude tests.");
        add(out, "Resume", c.ats(), ATS_MAX,
                "Run the ATS checker against a job description and apply the keyword and formatting suggestions.");
        add(out, "Profile", c.profile(), PROFILE_MAX,
                "Complete your profile: projects, certifications, links and a professional photo.");
        add(out, "Mock interviews", c.mock(), MOCK_MAX,
                "Take AI mock interviews (technical and HR) - at least three for full credit.");
        add(out, "Contests", c.contests(), CONTESTS_MAX,
                "Join weekly contests to practise under time pressure.");
        out.sort((a, b) -> Integer.compare(b.potentialGain(), a.potentialGain()));
        return out;
    }

    private static void add(List<Suggestion> out, String area, double value, int max, String text) {
        int gain = (int) Math.round(max - value);
        if (value < max * 0.8 && gain > 0) {
            out.add(new Suggestion(area, text, gain));
        }
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
