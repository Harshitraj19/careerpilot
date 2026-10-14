package com.placify.ai.offline;

import com.placify.ai.offline.InterviewBank.BankQuestion;
import com.placify.ai.offline.InterviewBank.KeyPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scores an interview answer from 0 to 10 without a language model.
 * <pre>
 * up to 7 points  coverage of the question's key points (keywords per point)
 * +1              enough depth (40+ words)
 * +1              concrete evidence (an example, a project, numbers)
 * +1              structure (STAR for behavioural answers; reasoning or complexity for technical ones)
 * </pre>
 * Answers shorter than 8 words score at most 2. Questions that are not in the bank (for example
 * ones written by the language model earlier) are scored on relevance to the question's terms.
 */
final class AnswerEvaluator {

    private static final Pattern NUMBER = Pattern.compile("\\d");
    private static final List<String> EXAMPLE_MARKERS = List.of("for example", "for instance", "e.g", "such as", "in my project",
            "in my internship", "i built", "i implemented", "i used", "we used", "consider", "suppose", "like when");
    private static final List<String> STAR_MARKERS = List.of("situation", "task", "my role", "i decided", "i did", "action",
            "result", "outcome", "as a result", "finally", "learned", "learnt", "the team", "deadline");
    private static final List<String> REASONING_MARKERS = List.of("because", "therefore", "so that", "which means", "trade-off",
            "tradeoff", "o(", "complexity", "whereas", "on the other hand", "in contrast", "advantage", "disadvantage");

    record Evaluation(int score, String feedback, List<String> idealPoints) {
    }

    private AnswerEvaluator() {
    }

    static Evaluation evaluate(BankQuestion bank, String question, String answer) {
        String text = TextTools.normalize(answer);
        int words = TextTools.words(answer);
        boolean behavioural = bank != null ? bank.isBehavioural() : looksBehavioural(question);

        List<String> covered = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        double coverage;
        List<String> ideal;
        if (bank != null) {
            for (KeyPoint p : bank.points()) {
                boolean hit = p.keywords().stream().anyMatch(k -> TextTools.mentions(text, k));
                (hit ? covered : missed).add(p.text());
            }
            coverage = (double) covered.size() / bank.points().size();
            ideal = bank.points().stream().map(KeyPoint::text).toList();
        } else {
            Set<String> q = TextTools.contentWords(question);
            Set<String> a = TextTools.contentWords(answer);
            long overlap = q.stream().filter(a::contains).count();
            coverage = q.isEmpty() ? 0.5 : Math.min(1.0, overlap / Math.max(2.0, q.size() * 0.6));
            ideal = List.of("Answer the exact question first, in one or two sentences",
                    "Explain the reasoning or the steps", "Give a concrete example or a result from your own work");
        }

        int score = (int) Math.round(coverage * 7);
        boolean deep = words >= 40;
        boolean evidence = NUMBER.matcher(answer == null ? "" : answer).find() || EXAMPLE_MARKERS.stream().anyMatch(text::contains);
        boolean structured = behavioural
                ? STAR_MARKERS.stream().filter(text::contains).count() >= 2
                : REASONING_MARKERS.stream().anyMatch(text::contains);
        score += (deep ? 1 : 0) + (evidence ? 1 : 0) + (structured ? 1 : 0);
        if (coverage == 0 && words >= 25) {
            score = Math.max(score, 2);
        }
        if (words < 8) {
            score = Math.min(score, 2);
        }
        score = Math.max(0, Math.min(10, score));
        return new Evaluation(score, feedback(score, covered, missed, deep, evidence, structured, behavioural, words), ideal);
    }

    private static String feedback(int score, List<String> covered, List<String> missed, boolean deep, boolean evidence,
                                   boolean structured, boolean behavioural, int words) {
        StringBuilder sb = new StringBuilder();
        if (words < 8) {
            return "The answer is too short to evaluate. Speak for at least 30-60 seconds: state the main idea, explain it and give an example.";
        }
        sb.append(score >= 8 ? "Strong answer. " : score >= 5 ? "Good attempt. " : "This answer needs more depth. ");
        if (!covered.isEmpty()) {
            sb.append("You covered ").append(lowerFirst(shorten(covered.get(0)))).append(covered.size() > 1 ? " and " + (covered.size() - 1) + " other key point(s). " : ". ");
        }
        if (!missed.isEmpty()) {
            sb.append("Also mention ").append(lowerFirst(shorten(missed.get(0)))).append(". ");
        }
        if (!deep) {
            sb.append("Add a little more detail. ");
        }
        if (!evidence) {
            sb.append(behavioural ? "Back it up with a specific example from your projects or college life. " : "Support it with a short example. ");
        }
        if (!structured) {
            sb.append(behavioural ? "Use the STAR format: situation, task, action, result." : "Explain why, or compare the options and their trade-offs.");
        }
        return sb.toString().trim();
    }

    private static boolean looksBehavioural(String question) {
        String q = TextTools.normalize(question);
        return q.contains(" tell me about a time") || q.contains(" describe a situation") || q.contains(" yourself")
                || q.contains(" weakness") || q.contains(" why do you") || q.contains(" why should");
    }

    private static String shorten(String point) {
        int colon = point.indexOf(':');
        String s = colon > 0 && colon < 40 ? point.substring(colon + 1).trim() : point;
        return s.length() > 110 ? s.substring(0, 110) + "..." : s;
    }

    private static String lowerFirst(String s) {
        boolean acronym = s.length() > 1 && Character.isUpperCase(s.charAt(1));
        return s.isEmpty() || acronym ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
