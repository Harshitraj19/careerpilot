package com.placify.ai.offline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Small text helpers shared by the built-in (offline) AI engine. */
final class TextTools {

    private static final Pattern NOT_WORD = Pattern.compile("[^a-z0-9+#/.%@='()^\\-]+");
    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with",
            "is", "are", "was", "were", "be", "it", "its", "this", "that", "as", "at", "by", "from", "i", "you", "we", "my",
            "your", "our", "can", "will", "would", "should", "what", "how", "why", "when", "which", "do", "does", "did", "have",
            "has", "had", "not", "but", "if", "so", "than", "then", "there", "their", "they", "them", "about", "explain",
            "describe", "tell", "me", "give", "example", "difference", "between", "each", "one", "use", "used");

    private TextTools() {
    }

    /** Lower-case text with punctuation turned into spaces, padded with spaces for word-start matching. */
    static String normalize(String text) {
        if (text == null) {
            return " ";
        }
        String s = text.toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
                .replace("²", "^2");
        return " " + NOT_WORD.matcher(s).replaceAll(" ").replaceAll("\\s+", " ").trim() + " ";
    }

    /**
     * True when the normalised text mentions the keyword. Keywords are word prefixes ("encapsulat"
     * matches "encapsulation"); keywords with symbols ("o(1)", "@component", "1=1") match anywhere.
     */
    static boolean mentions(String normalized, String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT).trim();
        if (k.isEmpty()) {
            return false;
        }
        if (!Character.isLetterOrDigit(k.charAt(0))) {
            return normalized.contains(k);
        }
        return normalized.contains(" " + k) || normalized.contains("(" + k) || normalized.contains("/" + k);
    }

    static int words(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    /** Distinct content words (no stop words, at least 3 letters). */
    static Set<String> contentWords(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : normalize(text).trim().split(" ")) {
            String t = w.replaceAll("^[^a-z0-9]+|[^a-z0-9+#]+$", "");
            if (t.length() >= 3 && !STOPWORDS.contains(t)) {
                out.add(stem(t));
            }
        }
        return out;
    }

    /** A light stemmer: enough to match "designing", "designed" and "designs" to "design". */
    static String stem(String w) {
        for (String suffix : List.of("ations", "ation", "ings", "ing", "edly", "ed", "ies", "es", "s", "ly")) {
            if (w.length() > suffix.length() + 3 && w.endsWith(suffix)) {
                return suffix.equals("ies") ? w.substring(0, w.length() - 3) + "y" : w.substring(0, w.length() - suffix.length());
            }
        }
        return w;
    }

    /** Splits text into trimmed, non-empty lines. */
    static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String line : text.replace("\r", "").split("\n")) {
            String t = line.replace('•', ' ').replace('●', ' ').replace('▪', ' ').strip();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    static String firstSentence(String text) {
        if (text == null) {
            return "";
        }
        int dot = text.indexOf(". ");
        return dot > 0 ? text.substring(0, dot + 1) : text;
    }
}
