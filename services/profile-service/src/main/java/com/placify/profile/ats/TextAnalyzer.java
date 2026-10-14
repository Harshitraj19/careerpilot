package com.placify.profile.ats;

import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Text processing used by the ATS scorer, built on Apache OpenNLP:
 * tokenising, stop-word removal, tech-synonym normalisation and stemming.
 */
public final class TextAnalyzer {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "by", "for", "with", "from",
            "as", "is", "are", "was", "were", "be", "been", "being", "this", "that", "these", "those", "it", "its",
            "we", "our", "you", "your", "they", "their", "he", "she", "his", "her", "i", "me", "my", "will", "would",
            "should", "can", "could", "may", "might", "must", "shall", "do", "does", "did", "have", "has", "had",
            "not", "no", "so", "than", "too", "very", "also", "etc", "such", "into", "about", "over", "under",
            "who", "whom", "which", "what", "when", "where", "why", "how", "all", "any", "both", "each", "few",
            "more", "most", "other", "some", "only", "own", "same", "then", "there", "here", "up", "out", "per",
            // job-description filler words
            "job", "description", "looking", "join", "team", "candidate", "ideal", "responsibilities", "requirements",
            "required", "preferred", "role", "opportunity", "work", "working", "ability", "able", "strong", "good",
            "excellent", "knowledge", "experience", "years", "year", "plus", "including", "using", "use", "based",
            "skills", "skill", "understanding", "environment", "company", "position", "apply", "familiarity",
            "proficiency", "proficient", "hands", "must-have", "nice", "well", "new", "across", "within",
            "intern", "interns", "internship", "developer", "developers", "engineer", "engineers", "student", "students",
            "fresher", "freshers", "graduate", "graduates");

    private static final Map<String, String> CANONICAL = new HashMap<>();

    static {
        String[][] groups = {
                {"react.js", "react", "reactjs"},
                {"node.js", "node", "nodejs"},
                {"express.js", "express", "expressjs"},
                {"next.js", "next", "nextjs"},
                {"javascript", "js", "ecmascript"},
                {"typescript", "ts"},
                {"postgresql", "postgres", "postgre"},
                {"mongodb", "mongo"},
                {"kubernetes", "k8s"},
                {"springboot", "spring-boot"},
                {"rest", "restful", "rest-api", "rest-apis"},
                {"api", "apis"},
                {"ml", "machine-learning"},
                {"dsa", "data-structures"},
                {"oop", "oops", "object-oriented"},
                {"ci/cd", "cicd"},
                {"aws", "amazon-web-services"},
                {"gcp", "google-cloud"},
                {"c++", "cpp"},
                {"c#", "csharp"},
                {"golang", "go"},
                {"sql", "mysql-sql"}
        };
        for (String[] group : groups) {
            for (String alias : group) {
                CANONICAL.put(alias, group[0]);
            }
        }
    }

    /** Multi-word skills recognised as one keyword. */
    public static final List<String> PHRASES = List.of(
            "machine learning", "deep learning", "data structures", "data analysis", "data science",
            "spring boot", "spring framework", "object oriented", "system design", "computer vision",
            "natural language processing", "rest api", "web development", "full stack", "cloud computing",
            "operating systems", "computer networks", "unit testing", "version control", "problem solving",
            "power bi", "artificial intelligence", "big data", "design patterns", "test driven development");

    private static final Set<String> SHORT_TECH = Set.of("c", "r", "go", "ai", "ml", "ui", "ux", "qa", "ci", "cd");

    private TextAnalyzer() {
    }

    /** Lower-case, canonical, stop-word free tokens (not stemmed). */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : SimpleTokenizer.INSTANCE.tokenize(text.toLowerCase(Locale.ROOT))) {
            String t = raw.replaceAll("^[^a-z0-9+#]+|[^a-z0-9+#]+$", "");
            if (t.isEmpty() || !t.matches(".*[a-z].*")) {
                continue;
            }
            t = CANONICAL.getOrDefault(t, t);
            if (STOPWORDS.contains(t)) {
                continue;
            }
            if (t.length() < 2 && !SHORT_TECH.contains(t)) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    public static String stem(String token) {
        if (token.contains("+") || token.contains("#") || token.contains(".") || token.length() <= 3) {
            return token;
        }
        return new PorterStemmer().stem(token);
    }

    public static List<String> stems(String text) {
        return tokens(text).stream().map(TextAnalyzer::stem).toList();
    }

    public static Set<String> phrasesIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) {
            return found;
        }
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("[\\-_/]", " ");
        for (String phrase : PHRASES) {
            if (lower.contains(phrase)) {
                found.add(phrase);
            }
        }
        return found;
    }

    /** Cosine similarity of log-scaled term-frequency vectors (0..1). */
    public static double cosine(List<String> a, List<String> b) {
        Map<String, Integer> fa = frequencies(a);
        Map<String, Integer> fb = frequencies(b);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (Map.Entry<String, Integer> e : fa.entrySet()) {
            double wa = 1 + Math.log(e.getValue());
            na += wa * wa;
            Integer cb = fb.get(e.getKey());
            if (cb != null) {
                dot += wa * (1 + Math.log(cb));
            }
        }
        for (Integer c : fb.values()) {
            double wb = 1 + Math.log(c);
            nb += wb * wb;
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static Map<String, Integer> frequencies(List<String> tokens) {
        Map<String, Integer> map = new HashMap<>();
        for (String t : tokens) {
            map.merge(t, 1, Integer::sum);
        }
        return map;
    }
}
