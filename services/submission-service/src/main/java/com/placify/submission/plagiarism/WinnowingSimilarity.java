package com.placify.submission.plagiarism;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in plagiarism detector (fallback when JPlag does not support a language).
 * Same idea as MOSS:
 * <ol>
 *   <li>normalise the code: drop comments and string contents, replace every
 *       identifier with "V" and every number with "N" (so renaming variables does not help)</li>
 *   <li>hash every k-gram of tokens</li>
 *   <li>keep the minimum hash of each window (winnowing) as the fingerprint</li>
 *   <li>similarity = Jaccard overlap of the two fingerprint sets</li>
 * </ol>
 */
public final class WinnowingSimilarity {

    private static final int K = 5;
    private static final int WINDOW = 4;

    private static final Set<String> KEYWORDS = Set.of(
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return", "int", "long", "double",
            "float", "char", "bool", "boolean", "string", "void", "class", "struct", "public", "private", "static", "new",
            "def", "in", "range", "print", "println", "printf", "cout", "cin", "include", "import", "using", "namespace",
            "fn", "let", "mut", "func", "var", "const", "true", "false", "null", "none", "and", "or", "not", "elif",
            "try", "catch", "except", "finally", "throw", "throws", "vector", "list", "map", "set", "len", "size");

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\d+(\\.\\d+)?|==|!=|<=|>=|&&|\\|\\||\\+\\+|--|[{}()\\[\\];,=<>+\\-*/%!&|^.:?]");

    private WinnowingSimilarity() {
    }

    public static double similarity(String codeA, String codeB) {
        Set<Long> a = fingerprints(codeA);
        Set<Long> b = fingerprints(codeB);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<Long> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    static List<String> normalize(String code) {
        String noBlock = code.replaceAll("(?s)/\\*.*?\\*/", " ");
        String noLine = noBlock.replaceAll("//[^\\n]*", " ").replaceAll("(?m)#(?!include)[^\\n]*", " ");
        String noStrings = noLine.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "S").replaceAll("'(\\\\.|[^'\\\\])*'", "S");
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(noStrings);
        while (m.find()) {
            String t = m.group();
            if (Character.isDigit(t.charAt(0))) {
                tokens.add("N");
            } else if (Character.isLetter(t.charAt(0)) || t.charAt(0) == '_') {
                tokens.add(KEYWORDS.contains(t.toLowerCase()) ? t.toLowerCase() : "V");
            } else {
                tokens.add(t);
            }
        }
        return tokens;
    }

    static Set<Long> fingerprints(String code) {
        List<String> tokens = normalize(code == null ? "" : code);
        List<Long> hashes = new ArrayList<>();
        for (int i = 0; i + K <= tokens.size(); i++) {
            long h = 1125899906842597L;
            for (int j = i; j < i + K; j++) {
                h = 31 * h + tokens.get(j).hashCode();
            }
            hashes.add(h);
        }
        Set<Long> prints = new HashSet<>();
        if (hashes.size() <= WINDOW) {
            prints.addAll(hashes);
            return prints;
        }
        for (int i = 0; i + WINDOW <= hashes.size(); i++) {
            long min = Long.MAX_VALUE;
            for (int j = i; j < i + WINDOW; j++) {
                min = Math.min(min, hashes.get(j));
            }
            prints.add(min);
        }
        return prints;
    }
}
