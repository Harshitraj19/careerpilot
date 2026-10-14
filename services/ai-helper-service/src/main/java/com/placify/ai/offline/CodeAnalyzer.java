package com.placify.ai.offline;

import com.placify.ai.web.AiDtos.CodeReview;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static code review used when no language model is configured: estimates time and space
 * complexity from the loop structure, checks readability, and lists common judge pitfalls.
 */
@Component
public class CodeAnalyzer {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern HASH_COMMENT = Pattern.compile("#[^\\n]*");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)'");
    private static final Pattern LOOP = Pattern.compile("\\b(for|while)\\b");
    private static final Pattern IDENT_DECL = Pattern.compile("\\b(?:int|long|double|float|char|boolean|String|var|let|const|auto)\\s+([A-Za-z_]\\w*)");
    private static final Pattern PY_ASSIGN = Pattern.compile("^\\s*([A-Za-z_]\\w*)\\s*=(?!=)", Pattern.MULTILINE);
    private static final Pattern FUNCTION = Pattern.compile("\\b(def\\s+\\w+|function\\s+\\w+|(?:static\\s+)?(?:public|private|protected)?\\s*\\w+[<>\\[\\]]*\\s+\\w+\\s*\\([^)]*\\)\\s*\\{)");

    /** [time, space] complexity estimates. */
    static String[] estimate(String code, String language) {
        String c = strip(code, language);
        int depth = python(language, c) ? pythonLoopDepth(c) : braceLoopDepth(c);
        boolean sorts = c.matches("(?s).*\\b(sort|sorted|Arrays\\.sort|Collections\\.sort|qsort)\\s*\\(.*");
        boolean halves = c.matches("(?s).*\\b(mid|bisect|binarySearch|lower_bound|upper_bound)\\b.*") || c.matches("(?s).*(/=\\s*2|>>=\\s*1|//\\s*2).*");
        String time;
        if (depth >= 2) {
            time = halves ? "O(n² log n)" : "O(n^" + depth + ")";
            time = depth == 2 && !halves ? "O(n²)" : time;
        } else if (depth == 1) {
            time = sorts || halves ? "O(n log n)" : "O(n)";
        } else {
            time = sorts ? "O(n log n)" : halves ? "O(log n)" : "O(1) apart from reading the input";
        }
        boolean collections = c.matches("(?s).*\\b(HashMap|HashSet|TreeMap|ArrayList|LinkedList|ArrayDeque|Stack|dict|set|list|vector|unordered_map|map|deque|new\\s+\\w+\\s*\\[|\\[\\]\\s*\\w+\\s*=).*")
                || c.contains("[0] *") || c.contains("[] for");
        String space = collections ? "O(n)" : "O(1)";
        return new String[] {time, space};
    }

    /** Typical reasons for wrong answers or runtime errors on an online judge. */
    static List<String> pitfalls(String code, String language, String constraints, String inputFormat) {
        List<String> out = new ArrayList<>();
        if (code == null || code.isBlank()) {
            out.add("There is no code yet. Start by reading the input exactly as the input format describes and printing it back.");
            return out;
        }
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String c = code;
        String lower = c.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*(print|println|printf|cout|console\\.log)\\s*\\(?\\s*\"\\s*(enter|input|please|give|type)\\b.*")
                || lower.matches("(?s).*input\\s*\\(\\s*\"[^\"]+\"\\s*\\).*")) {
            out.add("Remove prompts like \"Enter a number\". The judge compares your output exactly, so print only the answer.");
        }
        String cons = constraints == null ? "" : constraints;
        if ((cons.contains("10^9") || cons.contains("10^18") || cons.contains("2*10^5")) && !lower.contains("long") && !python(lang, c)
                && !lang.contains("javascript")) {
            out.add("Values or sums can exceed the 32-bit int range (about 2.1 × 10^9). Use `long` (Java/C++: long long) for sums, products and counts.");
        }
        if (lang.contains("java") && !lang.contains("script")) {
            Matcher cls = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(c);
            if (lower.contains("nextint") && lower.contains("nextline")) {
                out.add("Mixing `nextInt()` and `nextLine()` skips a line: after `nextInt()` the rest of the line is still unread. Call `nextLine()` once more, or read everything with `nextLine()` and parse it.");
            }
            if (!cls.find()) {
                out.add("Declare your class as `public class Main` (or keep a single public class) so it compiles on the judge.");
            }
            if ((cons.contains("10^5") || cons.contains("10^6") || cons.contains("10^7")) && lower.contains("scanner")) {
                out.add("For large inputs `Scanner` can be slow; `BufferedReader` with `StringTokenizer` is several times faster.");
            }
        }
        if (python(lang, c) && lower.contains("input()") && (inputFormat != null && inputFormat.toLowerCase(Locale.ROOT).contains("second line")) && !lower.contains("split")) {
            out.add("The numbers arrive on one line separated by spaces; read them with `list(map(int, input().split()))`.");
        }
        String[] est = estimate(code, language);
        if (est[0].startsWith("O(n²") || est[0].startsWith("O(n^")) {
            if (cons.contains("10^5") || cons.contains("10^6") || cons.contains("10^7")) {
                out.add("Your solution looks like " + est[0] + ", which is too slow for n around 10^5. Aim for O(n) or O(n log n).");
            }
        }
        if (lower.contains("recursion") || Pattern.compile("\\b(\\w+)\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*\\b\\1\\s*\\(").matcher(c).find()) {
            if (cons.contains("10^5") || cons.contains("500")) {
                out.add("Deep recursion can overflow the stack on large inputs; consider an iterative version with your own stack.");
            }
        }
        return out;
    }

    public CodeReview review(String title, String description, String code, String language) {
        String[] est = estimate(code, language);
        String c = strip(code, language);
        int lines = (int) code.lines().filter(l -> !l.isBlank()).count();
        int functions = countMatches(FUNCTION, c);
        List<String> names = new ArrayList<>();
        Matcher m = (python(language, c) ? PY_ASSIGN : IDENT_DECL).matcher(c);
        while (m.find()) {
            names.add(m.group(1));
        }
        long shortNames = names.stream().filter(n -> n.length() <= 2 && !List.of("i", "j", "k", "n", "m", "x", "y", "lo", "hi").contains(n)).count();
        boolean comments = code.contains("//") || code.contains("/*") || code.contains("# ");
        boolean longLines = code.lines().anyMatch(l -> l.length() > 120);
        boolean magic = Pattern.compile("[^\\w.](?:[3-9]\\d{2,}|1\\d{3,})(?![\\w.])").matcher(c).find();

        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        strengths.add("Accepted on all test cases, including the hidden ones");
        if (!est[0].contains("²") && !est[0].contains("^")) {
            strengths.add("Efficient approach: " + est[0] + " time");
        }
        if (functions > 1) {
            strengths.add("Logic is split into helper functions, which makes it easier to read and test");
        }
        if (shortNames == 0 && !names.isEmpty()) {
            strengths.add("Variable names are descriptive");
        }
        if (comments) {
            strengths.add("Comments explain the non-obvious parts");
        }
        if (functions <= 1 && lines > 25) {
            improvements.add("Move the core logic out of main() into a well-named function; it makes the solution reusable and testable");
        }
        if (shortNames > 0) {
            improvements.add("Use descriptive names instead of very short ones (loop counters like i and j are fine)");
        }
        if (!comments && lines > 15) {
            improvements.add("Add a one-line comment describing the idea of the algorithm");
        }
        if (magic) {
            improvements.add("Replace magic numbers with named constants (e.g. MAX_N or MOD)");
        }
        if (longLines) {
            improvements.add("Break lines longer than about 100 characters for readability");
        }
        if (est[0].contains("²") || est[0].contains("^")) {
            improvements.add("The nested loops make this " + est[0] + "; look for a hash map, sorting or a running value to reach O(n) or O(n log n)");
        }
        if (improvements.isEmpty()) {
            improvements.add("Try writing the same solution in a second language to strengthen your fundamentals");
        }
        int quality = 6 + (functions > 1 ? 1 : 0) + (shortNames == 0 ? 1 : 0) + (comments ? 1 : 0)
                + (est[0].contains("²") || est[0].contains("^") ? -1 : 1) - (magic ? 1 : 0) - (longLines ? 1 : 0);
        quality = Math.max(3, Math.min(10, quality));
        OfflineTutor.Problem p = new OfflineTutor.Problem(title, description, null, description, List.of());
        String summary = "Your " + (language == null ? "" : language + " ") + "solution to \"" + title + "\" runs in about " + est[0]
                + " time and " + est[1] + " extra space (estimated from its loop structure). It has " + lines + " lines of code"
                + (functions > 1 ? " split into " + functions + " functions." : " in a single function.");
        return new CodeReview(summary, est[0], est[1], strengths, improvements.stream().limit(4).toList(),
                OfflineTutor.edgeCases(p).stream().limit(4).toList(), quality);
    }

    // ---------------------------------------------------------------- loop structure

    private static int braceLoopDepth(String c) {
        Deque<Boolean> blocks = new ArrayDeque<>();
        int depth = 0;
        int max = 0;
        int i = 0;
        Matcher loop = LOOP.matcher(c);
        while (i < c.length()) {
            if (loop.find(i) && loop.start() == i) {
                // skip the loop header "(...)" and see whether a block or a single statement follows
                int open = c.indexOf('(', loop.end());
                int close = open < 0 ? -1 : matching(c, open);
                if (close < 0) {
                    i = loop.end();
                    continue;
                }
                int next = close + 1;
                while (next < c.length() && Character.isWhitespace(c.charAt(next))) {
                    next++;
                }
                if (next < c.length() && c.charAt(next) == '{') {
                    blocks.push(true);
                    depth++;
                    max = Math.max(max, depth);
                    i = next + 1;
                } else {
                    max = Math.max(max, depth + 1);
                    i = next;
                }
                continue;
            }
            char ch = c.charAt(i);
            if (ch == '{') {
                blocks.push(false);
            } else if (ch == '}' && !blocks.isEmpty() && blocks.pop()) {
                depth--;
            }
            i++;
        }
        return max;
    }

    private static int matching(String c, int open) {
        int level = 0;
        for (int k = open; k < c.length(); k++) {
            if (c.charAt(k) == '(') {
                level++;
            } else if (c.charAt(k) == ')' && --level == 0) {
                return k;
            }
        }
        return -1;
    }

    private static int pythonLoopDepth(String c) {
        Deque<Integer> loopIndents = new ArrayDeque<>();
        int max = 0;
        for (String line : c.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            while (!loopIndents.isEmpty() && indent <= loopIndents.peek()) {
                loopIndents.pop();
            }
            String t = line.strip();
            if (t.startsWith("for ") || t.startsWith("while ")) {
                loopIndents.push(indent);
                max = Math.max(max, loopIndents.size());
            }
            if (t.contains(" for ") && t.contains("[") && t.contains(" in ")) {
                max = Math.max(max, loopIndents.size() + 1);
            }
        }
        return max;
    }

    private static boolean python(String language, String code) {
        String l = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return l.contains("python") || (l.isEmpty() && code != null && code.contains("def ") && !code.contains("{"));
    }

    private static String strip(String code, String language) {
        if (code == null) {
            return "";
        }
        String c = STRING.matcher(code).replaceAll("\"\"");
        if (python(language, code)) {
            return HASH_COMMENT.matcher(c).replaceAll("");
        }
        c = BLOCK_COMMENT.matcher(c).replaceAll(" ");
        return LINE_COMMENT.matcher(c).replaceAll("");
    }

    private static int countMatches(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
