package com.placify.profile.ats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ATS resume scorer (pure Java, no framework code so it is easy to unit test).
 *
 * <pre>
 * Final score (0-100) =
 *     35% keyword match       (job-description keywords present in the resume)
 *   + 20% semantic similarity (sentence embeddings via DJL, computed by the caller)
 *   + 10% content similarity  (cosine of term-frequency vectors)
 *   + 15% structure           (sections and contact details)
 *   + 10% impact              (action verbs and quantified achievements)
 *   + 10% parseability        (enough selectable text in the PDF)
 * </pre>
 */
public final class AtsScorer {

    public static final double W_KEYWORDS = 0.35;
    public static final double W_SEMANTIC = 0.20;
    public static final double W_CONTENT = 0.10;
    public static final double W_STRUCTURE = 0.15;
    public static final double W_IMPACT = 0.10;
    public static final double W_PARSE = 0.10;

    private static final Set<String> ACTION_VERBS = Set.of(
            "built", "developed", "designed", "implemented", "delivered", "led", "owned", "created", "refactored",
            "optimized", "optimised", "automated", "migrated", "launched", "deployed", "integrated", "scaled",
            "improved", "reduced", "increased", "collaborated", "mentored", "debugged", "tested", "documented",
            "architected", "engineered", "analyzed", "analysed", "researched", "managed", "coordinated", "achieved",
            "won", "published", "trained", "streamlined", "resolved", "contributed", "initiated", "organized");

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s-]{8,}\\d)");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?\\s*(%|x|k|\\+|lakh|users|ms|sec)?");

    private AtsScorer() {
    }

    public record Result(
            int score,
            int keywordScore,
            int semanticScore,
            int contentScore,
            int structureScore,
            int impactScore,
            int parseabilityScore,
            List<String> matchedKeywords,
            List<String> missingKeywords,
            Map<String, Boolean> sections,
            List<String> tips) {
    }

    /**
     * @param semanticSimilarity 0..1 from the embedding model, or a negative value if
     *                           unavailable (then content similarity is used instead)
     */
    public static Result score(String resumeText, String jobDescription, double semanticSimilarity) {
        String resume = resumeText == null ? "" : resumeText;
        String jd = jobDescription == null ? "" : jobDescription;

        // ---- keywords
        List<String> keywords = extractKeywords(jd, 30);
        Set<String> resumeStems = new HashSet<>(TextAnalyzer.stems(resume));
        Set<String> resumePhrases = TextAnalyzer.phrasesIn(resume);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String k : keywords) {
            boolean present = k.contains(" ") ? resumePhrases.contains(k) : resumeStems.contains(TextAnalyzer.stem(k));
            (present ? matched : missing).add(k);
        }
        int keywordScore = keywords.isEmpty() ? 0 : (int) Math.round(100.0 * matched.size() / keywords.size());

        // ---- content similarity
        double content = TextAnalyzer.cosine(TextAnalyzer.stems(resume), TextAnalyzer.stems(jd));
        // raw cosine of two different documents rarely exceeds 0.6, so scale it
        int contentScore = clamp((int) Math.round(Math.min(1.0, content / 0.6) * 100));

        // ---- semantic similarity
        int semanticScore = semanticSimilarity < 0
                ? contentScore
                : clamp((int) Math.round(Math.max(0, (semanticSimilarity - 0.2) / 0.6) * 100));

        // ---- structure
        Map<String, Boolean> sections = sections(resume);
        int structureScore = structureScore(sections);

        // ---- impact
        int impactScore = impactScore(resume);

        // ---- parseability
        int parseScore = parseability(resume);

        double total = keywordScore * W_KEYWORDS + semanticScore * W_SEMANTIC + contentScore * W_CONTENT
                + structureScore * W_STRUCTURE + impactScore * W_IMPACT + parseScore * W_PARSE;

        List<String> tips = tips(sections, missing, impactScore, parseScore, keywordScore);
        return new Result(clamp((int) Math.round(total)), keywordScore, semanticScore, contentScore,
                structureScore, impactScore, parseScore, matched, missing.subList(0, Math.min(15, missing.size())),
                sections, tips);
    }

    /** Most important job-description keywords: known multi-word skills first, then frequent terms. */
    static List<String> extractKeywords(String jd, int limit) {
        List<String> result = new ArrayList<>(TextAnalyzer.phrasesIn(jd));
        Map<String, Integer> freq = TextAnalyzer.frequencies(TextAnalyzer.tokens(jd));
        Set<String> phraseWords = new HashSet<>();
        result.forEach(p -> phraseWords.addAll(List.of(p.split(" "))));
        freq.entrySet().stream()
                .filter(e -> !phraseWords.contains(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .filter(k -> !k.matches("\\d+"))
                .limit(Math.max(0, limit - result.size()))
                .forEach(result::add);
        return result;
    }

    static Map<String, Boolean> sections(String resume) {
        String lower = resume.toLowerCase(Locale.ROOT);
        Map<String, Boolean> s = new LinkedHashMap<>();
        s.put("summary", has(lower, "summary|objective|profile|about me"));
        s.put("skills", has(lower, "skills|technical skills|technologies|tech stack"));
        s.put("experience", has(lower, "experience|internship|internships|work history|employment"));
        s.put("projects", has(lower, "projects|project work|academic projects"));
        s.put("education", has(lower, "education|academic|qualification"));
        s.put("certifications", has(lower, "certifications|certificates|courses"));
        s.put("achievements", has(lower, "achievements|awards|honors|honours|accomplishments"));
        s.put("email", EMAIL.matcher(resume).find());
        s.put("phone", PHONE.matcher(resume).find());
        s.put("linkedin", lower.contains("linkedin"));
        s.put("github", lower.contains("github"));
        return s;
    }

    private static boolean has(String text, String alternatives) {
        return Pattern.compile("(^|\\n|\\s)(" + alternatives + ")\\b").matcher(text).find();
    }

    static int structureScore(Map<String, Boolean> s) {
        double score = 0;
        // core sections (60)
        score += s.get("skills") ? 15 : 0;
        score += s.get("education") ? 15 : 0;
        score += s.get("projects") ? 15 : 0;
        score += s.get("experience") ? 15 : 0;
        // nice-to-have sections (20)
        score += s.get("summary") ? 7 : 0;
        score += s.get("certifications") ? 7 : 0;
        score += s.get("achievements") ? 6 : 0;
        // contact (20)
        score += s.get("email") ? 6 : 0;
        score += s.get("phone") ? 5 : 0;
        score += s.get("linkedin") ? 4 : 0;
        score += s.get("github") ? 5 : 0;
        return clamp((int) Math.round(score));
    }

    static int impactScore(String resume) {
        String[] lines = resume.split("\\r?\\n");
        int content = 0;
        int action = 0;
        int quantified = 0;
        for (String line : lines) {
            String t = line.trim().replaceAll("^[\\-\\u2022*>·•]+\\s*", "");
            if (t.split("\\s+").length < 4) {
                continue;
            }
            content++;
            String first = t.split("\\s+")[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (ACTION_VERBS.contains(first)) {
                action++;
            }
            if (NUMBER.matcher(t).find()) {
                quantified++;
            }
        }
        if (content == 0) {
            return 0;
        }
        double actionRatio = (double) action / content;
        double quantRatio = (double) quantified / content;
        return clamp((int) Math.round(Math.min(60, actionRatio * 150) + Math.min(40, quantRatio * 120)));
    }

    static int parseability(String resume) {
        int chars = resume.replaceAll("\\s+", " ").trim().length();
        if (chars < 300) {
            return 10;
        }
        if (chars < 800) {
            return 50;
        }
        if (chars > 15000) {
            return 80;
        }
        return 100;
    }

    private static List<String> tips(Map<String, Boolean> s, List<String> missing, int impact, int parse, int keywords) {
        List<String> tips = new ArrayList<>();
        if (parse <= 50) {
            tips.add("Very little text could be read from your PDF. Export it as a text-based PDF, not a scanned image.");
        }
        if (keywords < 60 && !missing.isEmpty()) {
            tips.add("Add the job's missing keywords where they are genuinely true for you: "
                    + String.join(", ", missing.subList(0, Math.min(6, missing.size()))) + ".");
        }
        if (!s.get("skills")) {
            tips.add("Add a clear 'Skills' section listing languages, frameworks and tools.");
        }
        if (!s.get("projects")) {
            tips.add("Add a 'Projects' section with 2-3 projects, the tech stack and your role.");
        }
        if (!s.get("experience")) {
            tips.add("Add internships or work experience, even short ones or open-source contributions.");
        }
        if (!s.get("education")) {
            tips.add("Add an 'Education' section with your degree, college, CGPA and year.");
        }
        if (impact < 50) {
            tips.add("Start bullet points with action verbs (Built, Designed, Optimised) and add numbers "
                    + "(e.g. 'reduced load time by 40%').");
        }
        if (!s.get("github") || !s.get("linkedin")) {
            tips.add("Include your GitHub and LinkedIn profile links.");
        }
        if (tips.isEmpty()) {
            tips.add("Your resume is well structured for this job. Tailor the summary to the role for an extra edge.");
        }
        return tips;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
