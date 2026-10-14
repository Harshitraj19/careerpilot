package com.placify.ai.offline;

import com.placify.ai.web.AiDtos.TutorAnswer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in coding tutor used when no language model is configured: progressive hints by problem
 * type, debugging help for common mistakes, complexity guidance from the constraints, and edge
 * cases. Like the AI tutor it teaches step by step instead of handing out full code.
 */
@Component
public class OfflineTutor {

    /** Problem facts the tutor needs. */
    public record Problem(String title, String description, String inputFormat, String constraints, List<String> tags) {
    }

    /** Four hint levels per problem type: big idea, strategy, pseudocode, key snippet idea. */
    private static final Map<String, List<String>> HINTS = new LinkedHashMap<>();

    static {
        HINTS.put("stack", List.of(
                "Which bracket has to be closed first? Always the **most recent** unmatched opening one. Which data structure gives you the most recent item in O(1)?",
                "1. Scan the string left to right.\n2. Push every opening bracket on a **stack**.\n3. For a closing bracket, check that the stack is not empty and that its top is the matching opener; then pop.\n4. At the end the stack must be empty.",
                "```\nfor ch in s:\n    if ch is an opener: push(ch)\n    else:\n        if stack empty or top != matching(ch): answer NO\n        pop()\nanswer YES if stack empty else NO\n```\nTime O(n), space O(n).",
                "Keep a map from each closer to its opener, e.g. `')' -> '('`, so the check is just `stack.peek() == opener.get(ch)`."));
        HINTS.put("dynamic programming", List.of(
                "Can the answer for the whole input be built from answers for **smaller prefixes**? Try defining `best[i]` = the answer that ends at (or uses the first) i elements.",
                "1. Define the state precisely (what does `dp[i]` mean?).\n2. Write the transition from smaller states.\n3. Set the base case.\n4. Decide the order of filling the table and where the final answer is.",
                "```\ndp[0] = base\nfor i in 1..n-1:\n    dp[i] = best of (options that use dp[j] for j < i)\nanswer = dp[n-1] (or the max over all i)\n```",
                "If only the previous one or two states are used, keep them in variables instead of a full array to get O(1) space."));
        HINTS.put("kadane", List.of(
                "For each position, ask: is it better to **extend** the best subarray ending just before it, or to **start fresh** here?",
                "1. Keep `current` = best sum of a subarray ending at i.\n2. `current = max(a[i], current + a[i])`.\n3. Keep `best = max(best, current)`.\n4. Start both with a[0] so all-negative arrays work.",
                "```\ncurrent = best = a[0]\nfor i in 1..n-1:\n    current = max(a[i], current + a[i])\n    best = max(best, current)\nprint best\n```\nTime O(n), space O(1).",
                "Use a 64-bit type (`long` in Java/C++) because sums can exceed 2^31 when |a[i]| is up to 10^9."));
        HINTS.put("binary search", List.of(
                "The array is **sorted**. Instead of scanning, can you discard half of the remaining range with one comparison?",
                "1. Keep `lo` and `hi`.\n2. Look at `mid`; decide which half can still contain the answer.\n3. To find the **first** occurrence, keep searching left even after a match and remember the match.",
                "```\nlo, hi, ans = 0, n-1, -1\nwhile lo <= hi:\n    mid = lo + (hi - lo) / 2\n    if a[mid] >= x: \n        if a[mid] == x: ans = mid\n        hi = mid - 1\n    else: lo = mid + 1\n```",
                "Write `mid = lo + (hi - lo) / 2` to avoid overflow, and test with x smaller than all, larger than all, and repeated values."));
        HINTS.put("graphs", List.of(
                "Treat each cell or node as a vertex. Every time you find an unvisited land cell, you have found a **new component**; can you mark the whole component at once?",
                "1. Loop over every cell.\n2. When you find an unvisited '1', increase the count.\n3. Run DFS or BFS from it and mark all connected '1' cells as visited (up, down, left, right).",
                "```\ncount = 0\nfor each cell (r, c):\n    if grid[r][c] == '1':\n        count += 1\n        flood(r, c)   # DFS/BFS turning connected '1's into '0'\n```",
                "For large grids prefer an explicit stack or queue over recursion to avoid stack overflow."));
        HINTS.put("hashing", List.of(
                "While scanning, can you remember what you have **already seen** so that each new element is checked in O(1)?",
                "1. Keep a hash map from value to how many times it has appeared.\n2. For each element, the number of good partners seen before it is `count[target - a[i]]`.\n3. Add that to the answer, then record a[i].",
                "```\nseen = empty map; pairs = 0\nfor v in a:\n    pairs += seen.get(k - v, 0)\n    seen[v] += 1\n```\nTime O(n), space O(n).",
                "Use a `long` for the answer: with many duplicates the number of pairs can be close to n²/2."));
        HINTS.put("strings", List.of(
                "Work on the characters directly. What is the simplest property you can check or build in one pass?",
                "1. Read the whole input line exactly as given.\n2. Normalise what the problem tells you to ignore (case, spaces, symbols).\n3. Use two pointers from both ends, or build the reversed string, or count characters.",
                "```\nl, r = 0, len(s) - 1\nwhile l < r:\n    skip characters that should be ignored\n    if s[l] != s[r]: return NO\n    l += 1; r -= 1\nreturn YES\n```",
                "In Java read the full line with `sc.nextLine()` or `BufferedReader.readLine()`; `next()` stops at the first space."));
        HINTS.put("math", List.of(
                "Is there a formula or a simple recurrence? Check the constraints to see which types (int or long) the values need.",
                "1. Work out a few small cases by hand.\n2. Find the pattern or recurrence.\n3. Compute iteratively and watch for overflow.",
                "```\na, b = 0, 1\nrepeat n times: a, b = b, a + b\nprint a\n```",
                "Values up to 10^18 fit in a signed 64-bit integer (`long`), but not in a 32-bit `int`."));
        HINTS.put("sieve", List.of(
                "Checking each number separately is too slow for n up to 10^7. Can you cross out all multiples of each prime at once?",
                "1. Make a boolean array `composite[0..n]`.\n2. For each i from 2 while i*i <= n, if i is not composite, mark i*i, i*i+i, ... as composite.\n3. Count the unmarked numbers from 2 to n.",
                "```\nfor i in 2..sqrt(n):\n    if not composite[i]:\n        for j in i*i..n step i: composite[j] = true\ncount = number of i in 2..n with not composite[i]\n```\nTime O(n log log n).",
                "Start the inner loop at i*i (smaller multiples are already marked) and use a byte/boolean array to save memory."));
        HINTS.put("sorting", List.of(
                "Would the problem become easy if the input were **sorted**? After sorting, overlapping or related items end up next to each other.",
                "1. Sort the items by their start (or value).\n2. Sweep once, merging the current item into the previous group when they overlap.\n3. Count or output the groups.",
                "```\nsort intervals by start\ngroups = 0; end = -infinity\nfor (l, r) in intervals:\n    if l > end: groups += 1; end = r\n    else: end = max(end, r)\n```",
                "Decide whether touching intervals like [1,4] and [4,5] count as overlapping; here they do, so use `l > end` to start a new group."));
        HINTS.put("arrays", List.of(
                "Can you solve it in **one pass**, keeping only a few variables that summarise what you have seen so far?",
                "1. Decide what to track (for example the largest and second-largest distinct values).\n2. Update them carefully for each element.\n3. Handle the case where the answer does not exist.",
                "```\nfirst = second = NONE\nfor v in a:\n    if v > first: second = first; first = v\n    elif v != first and v > second: second = v\nprint second or -1\n```",
                "Test with all-equal values, negatives and duplicates of the maximum."));
        HINTS.put("implementation", List.of(
                "Read the input exactly as described in the input format and print only what the output format asks for.",
                "1. Parse the input.\n2. Compute the answer.\n3. Print it with no extra text.",
                "```\nread values\ncompute\nprint result\n```",
                "Do not print prompts like \"Enter a number\" - the judge compares your output exactly."));
    }

    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("dp", "dynamic programming"), Map.entry("dynamic programming", "dynamic programming"),
            Map.entry("kadane", "kadane"), Map.entry("binary search", "binary search"), Map.entry("graphs", "graphs"),
            Map.entry("dfs", "graphs"), Map.entry("bfs", "graphs"), Map.entry("grid", "graphs"), Map.entry("hashing", "hashing"),
            Map.entry("stack", "stack"), Map.entry("strings", "strings"), Map.entry("two pointers", "strings"),
            Map.entry("math", "math"), Map.entry("sieve", "sieve"), Map.entry("sorting", "sorting"), Map.entry("intervals", "sorting"),
            Map.entry("arrays", "arrays"), Map.entry("implementation", "implementation"));

    public TutorAnswer answer(Problem p, int currentLevel, String query, String code, String language) {
        String q = TextTools.normalize(query);
        String kind = kind(p);
        List<String> hints = HINTS.getOrDefault(kind, HINTS.get("implementation"));
        boolean hasCode = code != null && !code.isBlank() && code.length() > 40;

        if (asksAbout(q, "error", "wrong", "fail", "bug", "exception", "not working", "doesn't work", "incorrect", "compile")) {
            List<String> issues = CodeAnalyzer.pitfalls(code, language, p.constraints(), p.inputFormat());
            String body = issues.isEmpty()
                    ? "I could not spot an obvious mistake. Run your code on the sample and on these edge cases, and compare the output line by line:\n"
                    + bullet(edgeCases(p))
                    : "Here is what to check in your code:\n" + bullet(issues);
            return new TutorAnswer(body, currentLevel, issues.isEmpty() ? "Test the edge cases above one by one." : "Fix the first item, then re-run the sample.");
        }
        if (asksAbout(q, "complexity", "time limit", "tle", "slow", "optimi", "faster", "efficient")) {
            String body = "**Target complexity:** " + target(p.constraints()) + "\n\n"
                    + (hasCode ? "Your code looks like **" + CodeAnalyzer.estimate(code, language)[0] + "**. " : "")
                    + "Look for repeated work inside loops; a hash map, sorting or a prefix/running value usually removes one level of nesting.\n\n"
                    + "Idea for this problem: " + hints.get(0);
            return new TutorAnswer(body, Math.max(currentLevel, 1), "Rewrite the innermost loop using the idea above.");
        }
        if (asksAbout(q, "edge", "test case", "corner", "hidden")) {
            return new TutorAnswer("Hidden tests usually include these cases:\n" + bullet(edgeCases(p)), currentLevel,
                    "Run your code on each case before submitting.");
        }
        boolean wantsSolution = asksAbout(q, "full solution", "complete solution", "full code", "give me the code", "solve it for me");
        int level = wantsSolution ? 4 : Math.min(4, currentLevel + 1);
        StringBuilder body = new StringBuilder();
        if (wantsSolution) {
            body.append("I will not paste a finished program, but here is everything you need to write it yourself:\n\n")
                    .append(hints.get(1)).append("\n\n").append(hints.get(2)).append("\n\n").append(hints.get(3));
        } else {
            body.append("**Hint ").append(level).append(" of 4.** ").append(hints.get(level - 1));
            if (hasCode && level >= 2) {
                List<String> issues = CodeAnalyzer.pitfalls(code, language, p.constraints(), p.inputFormat());
                if (!issues.isEmpty()) {
                    body.append("\n\nAbout your current code: ").append(issues.get(0));
                }
            }
        }
        String next = level >= 4 ? "Write the code, run it on the sample and then on the edge cases." : "Try it, and ask for the next hint if you are still stuck.";
        return new TutorAnswer(body.toString(), level, next);
    }

    private static String kind(Problem p) {
        List<String> tags = p.tags() == null ? List.of() : p.tags();
        for (String preferred : List.of("kadane", "sieve", "binary search", "stack", "dfs", "bfs", "grid", "graphs", "hashing",
                "intervals", "sorting", "dynamic programming", "dp", "two pointers", "strings", "math", "arrays", "implementation")) {
            for (String t : tags) {
                if (t.trim().equalsIgnoreCase(preferred)) {
                    return TAG_ALIASES.get(preferred);
                }
            }
        }
        return "implementation";
    }

    static List<String> edgeCases(Problem p) {
        String all = ((p.description() == null ? "" : p.description()) + " " + (p.constraints() == null ? "" : p.constraints())).toLowerCase(Locale.ROOT);
        List<String> cases = new ArrayList<>();
        cases.add("The smallest input allowed by the constraints (for example n = 1 or a single character)");
        if (all.contains("10^9") || all.contains("10^18") || all.contains("2*10^5") || all.contains("10^5")) {
            cases.add("The largest input - check both speed and overflow (use 64-bit integers for sums and counts)");
        }
        if (all.contains("-") || all.contains("|a[i]|") || all.contains("negative")) {
            cases.add("Negative numbers, and an input where every number is negative");
        }
        cases.add("All values equal, or many duplicates");
        if (all.contains("string") || all.contains("bracket") || all.contains("sentence")) {
            cases.add("Strings with only one kind of character, and strings with spaces or symbols if they are allowed");
        }
        cases.add("A case where the answer does not exist (if the problem allows it, e.g. print -1 or NO)");
        return cases;
    }

    private static String target(String constraints) {
        String c = constraints == null ? "" : constraints.toLowerCase(Locale.ROOT);
        if (c.contains("10^7")) {
            return "about O(n log log n) or O(n) - n is up to 10^7";
        }
        if (c.contains("10^5") || c.contains("2*10^5") || c.contains("10^6")) {
            return "O(n) or O(n log n) - O(n²) is too slow for n around 10^5";
        }
        if (c.contains("500") || c.contains("1000")) {
            return "O(n²) is fine for n up to about 1000";
        }
        return "linear or n log n is always safe";
    }

    private static boolean asksAbout(String normalizedQuery, String... words) {
        for (String w : words) {
            if (normalizedQuery.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static String bullet(List<String> items) {
        StringBuilder sb = new StringBuilder();
        items.forEach(i -> sb.append("- ").append(i).append('\n'));
        return sb.toString();
    }
}
