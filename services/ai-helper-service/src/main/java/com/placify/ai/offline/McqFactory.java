package com.placify.ai.offline;

import com.placify.ai.web.AiDtos.GeneratedMcq;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Generates original MCQs without a language model. Quantitative, logical and technical questions
 * come from templates with random numbers, so the correct answer is computed, never guessed;
 * verbal questions use a vocabulary bank. Each question has 4 distinct options and an explanation.
 */
@Component
public class McqFactory {

    private static final String[][] WORDS = {
            {"ABUNDANT", "Plentiful", "Scarce"}, {"BENEVOLENT", "Kind", "Cruel"}, {"CANDID", "Frank", "Secretive"},
            {"DILIGENT", "Hard-working", "Lazy"}, {"EPHEMERAL", "Short-lived", "Permanent"}, {"FRUGAL", "Thrifty", "Wasteful"},
            {"GREGARIOUS", "Sociable", "Reserved"}, {"HOSTILE", "Unfriendly", "Welcoming"}, {"IMPARTIAL", "Unbiased", "Prejudiced"},
            {"JOVIAL", "Cheerful", "Gloomy"}, {"LUCID", "Clear", "Confusing"}, {"METICULOUS", "Thorough", "Careless"},
            {"NOVICE", "Beginner", "Expert"}, {"OBSOLETE", "Outdated", "Modern"}, {"PRUDENT", "Wise", "Reckless"},
            {"RESILIENT", "Tough", "Fragile"}, {"SINCERE", "Genuine", "Fake"}, {"TRANQUIL", "Calm", "Agitated"},
            {"VERBOSE", "Wordy", "Concise"}, {"ZEALOUS", "Enthusiastic", "Apathetic"}, {"AMBIGUOUS", "Unclear", "Definite"},
            {"COHERENT", "Logical", "Confused"}, {"CONCUR", "Agree", "Differ"}, {"ELOQUENT", "Articulate", "Inarticulate"},
            {"FEASIBLE", "Practical", "Impossible"}, {"INEVITABLE", "Unavoidable", "Avoidable"}, {"LENIENT", "Tolerant", "Strict"},
            {"PRAGMATIC", "Practical", "Idealistic"}, {"TENACIOUS", "Persistent", "Irresolute"}, {"VIGILANT", "Watchful", "Negligent"}};

    public List<GeneratedMcq> generate(String topic, String difficulty, int count) {
        Random r = ThreadLocalRandom.current();
        int level = switch (difficulty == null ? "MEDIUM" : difficulty.toUpperCase(Locale.ROOT)) {
            case "EASY" -> 1;
            case "HARD" -> 3;
            default -> 2;
        };
        List<Function<Random, GeneratedMcq>> templates = switch (topic == null ? "" : topic.toUpperCase(Locale.ROOT)) {
            case "LOGICAL" -> List.of(x -> series(x, level), x -> dayAfter(x), x -> ranking(x), x -> letterShift(x), x -> direction(x));
            case "VERBAL" -> List.of(x -> word(x, true), x -> word(x, false));
            case "TECHNICAL" -> List.of(x -> binary(x, level), x -> loopComplexity(x), x -> treeEdges(x), x -> port(x), x -> dataStructure(x));
            default -> List.of(x -> percentage(x, level), x -> simpleInterest(x, level), x -> profit(x, level), x -> speed(x, level),
                    x -> work(x), x -> average(x, level), x -> ratio(x));
        };
        List<GeneratedMcq> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int guard = 0;
        while (out.size() < count && guard++ < count * 20) {
            GeneratedMcq q = templates.get(r.nextInt(templates.size())).apply(r);
            if (q != null && seen.add(q.question())) {
                out.add(q);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- quantitative

    private GeneratedMcq percentage(Random r, int level) {
        int base = (r.nextInt(9) + 2) * (level == 1 ? 50 : 40);
        int pct = new int[] {10, 15, 20, 25, 30, 40, 60, 75}[r.nextInt(8)];
        int now = base + base * pct / 100;
        if (base * pct % 100 != 0) {
            return null;
        }
        return mcq("The price of an item rises from ₹" + base + " to ₹" + now + ". What is the percentage increase?",
                pct + "%", List.of((pct + 5) + "%", (pct - 5) + "%", Math.round(100.0 * (now - base) / now) + "%"),
                "Increase = " + (now - base) + "; " + (now - base) + " / " + base + " × 100 = " + pct + "%.", r);
    }

    private GeneratedMcq simpleInterest(Random r, int level) {
        int p = (r.nextInt(9) + 1) * 2000;
        int rate = r.nextInt(level == 3 ? 10 : 6) + 4;
        int t = r.nextInt(4) + 2;
        int si = p * rate * t / 100;
        return mcq("What is the simple interest on ₹" + p + " at " + rate + "% per annum for " + t + " years?", "₹" + si,
                List.of("₹" + (si + p * rate / 100), "₹" + (si - p * rate / 100), "₹" + (p * rate / 100)),
                "SI = P × R × T / 100 = " + p + " × " + rate + " × " + t + " / 100 = ₹" + si + ".", r);
    }

    private GeneratedMcq profit(Random r, int level) {
        int cost = (r.nextInt(16) + 4) * 50;
        int pct = new int[] {10, 20, 25, 30, 40, 50}[r.nextInt(6)];
        int sell = cost + cost * pct / 100;
        boolean askPrice = level >= 2 && r.nextBoolean();
        if (askPrice) {
            return mcq("An article bought for ₹" + cost + " is sold at a profit of " + pct + "%. What is the selling price?", "₹" + sell,
                    List.of("₹" + (sell + cost / 10), "₹" + (cost + pct), "₹" + (sell - cost / 20)),
                    cost + " × (1 + " + pct + "/100) = ₹" + sell + ".", r);
        }
        return mcq("An article bought for ₹" + cost + " is sold for ₹" + sell + ". What is the profit percentage?", pct + "%",
                List.of((pct + 5) + "%", (pct - 5) + "%", Math.round(100.0 * (sell - cost) / sell) + "%"),
                "Profit = " + (sell - cost) + "; " + (sell - cost) + " / " + cost + " × 100 = " + pct + "%.", r);
    }

    private GeneratedMcq speed(Random r, int level) {
        int kmh = new int[] {36, 54, 72, 90, 108}[r.nextInt(5)];
        int ms = kmh * 5 / 18;
        if (level >= 2) {
            int len = (r.nextInt(8) + 2) * ms * 2;
            int time = len / ms;
            return mcq("A " + len + " m long train runs at " + kmh + " km/h. How long does it take to cross a pole?", time + " s",
                    List.of((time + 2) + " s", (time - 1) + " s", (time * 2) + " s"),
                    kmh + " km/h = " + ms + " m/s, and " + len + " / " + ms + " = " + time + " seconds.", r);
        }
        int hours = r.nextInt(4) + 2;
        return mcq("A car travels at " + kmh + " km/h. How far does it go in " + hours + " hours?", (kmh * hours) + " km",
                List.of((kmh * hours + kmh / 2) + " km", (kmh * (hours - 1)) + " km", (kmh + hours) + " km"),
                "Distance = speed × time = " + kmh + " × " + hours + " = " + (kmh * hours) + " km.", r);
    }

    private GeneratedMcq work(Random r) {
        int[][] pairs = {{10, 15, 6}, {12, 24, 8}, {20, 30, 12}, {6, 12, 4}, {15, 30, 10}, {12, 36, 9}, {10, 40, 8}};
        int[] p = pairs[r.nextInt(pairs.length)];
        return mcq("A can finish a job in " + p[0] + " days and B in " + p[1] + " days. Working together, how many days do they take?",
                p[2] + " days", List.of((p[2] + 2) + " days", (p[0] + p[1]) / 2 + " days", (p[2] - 1) + " days"),
                "Together they do 1/" + p[0] + " + 1/" + p[1] + " = 1/" + p[2] + " of the job per day, so " + p[2] + " days.", r);
    }

    private GeneratedMcq average(Random r, int level) {
        int n = level == 1 ? 4 : 5;
        int avg = r.nextInt(30) + 20;
        List<Integer> nums = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < n - 1; i++) {
            int v = avg + r.nextInt(21) - 10;
            nums.add(v);
            sum += v;
        }
        int last = avg * n - sum;
        if (last <= 0) {
            return null;
        }
        String known = (n - 1 == 4 ? "Four" : "Three") + " of them are " + joinInts(nums);
        return mcq("The average of " + n + " numbers is " + avg + ". " + known + ". What is the remaining number?",
                String.valueOf(last), List.of(String.valueOf(last + 3), String.valueOf(last - 4), String.valueOf(avg)),
                "Total = " + n + " × " + avg + " = " + (avg * n) + "; the others add up to " + sum + ", so the number is " + last + ".", r);
    }

    private GeneratedMcq ratio(Random r) {
        int a = r.nextInt(4) + 1;
        int b = a + r.nextInt(4) + 1;
        int unit = (r.nextInt(9) + 2) * 20;
        int total = (a + b) * unit;
        return mcq("₹" + total + " is divided between two people in the ratio " + a + " : " + b + ". What is the larger share?", "₹" + (b * unit),
                List.of("₹" + (a * unit), "₹" + (total / 2), "₹" + (b * unit + unit)),
                (a + b) + " parts = " + total + ", one part = " + unit + ", larger share = " + b + " × " + unit + " = ₹" + (b * unit) + ".", r);
    }

    // ---------------------------------------------------------------- logical

    private GeneratedMcq series(Random r, int level) {
        int start = r.nextInt(9) + 1;
        if (level == 1) {
            int d = r.nextInt(8) + 2;
            List<Integer> s = List.of(start, start + d, start + 2 * d, start + 3 * d, start + 4 * d);
            int next = start + 5 * d;
            return mcq("Find the next number: " + joinInts(s) + ", ?", String.valueOf(next),
                    List.of(String.valueOf(next + 1), String.valueOf(next - d / 2 - 1), String.valueOf(next + d)),
                    "Each term increases by " + d + ".", r);
        }
        int m = r.nextInt(2) + 2;
        List<Integer> s = List.of(start, start * m + 1, (start * m + 1) * m + 1, ((start * m + 1) * m + 1) * m + 1);
        int next = s.get(3) * m + 1;
        return mcq("Find the next number: " + joinInts(s) + ", ?", String.valueOf(next),
                List.of(String.valueOf(next - 1), String.valueOf(s.get(3) * m), String.valueOf(next + m)),
                "Each term is the previous one × " + m + " + 1.", r);
    }

    private GeneratedMcq dayAfter(Random r) {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        int today = r.nextInt(7);
        int add = r.nextInt(40) + 8;
        String answer = days[(today + add) % 7];
        List<String> wrong = new ArrayList<>();
        for (int k = 1; wrong.size() < 3; k++) {
            wrong.add(days[(today + add + k) % 7]);
        }
        return mcq("Today is " + days[today] + ". What day of the week will it be after " + add + " days?", answer, wrong,
                add + " mod 7 = " + (add % 7) + ", and " + days[today] + " + " + (add % 7) + " days = " + answer + ".", r);
    }

    private GeneratedMcq ranking(Random r) {
        int left = r.nextInt(15) + 5;
        int right = r.nextInt(15) + 5;
        int total = left + right - 1;
        return mcq("In a row of students, Asha is " + ordinal(left) + " from the left and " + ordinal(right) + " from the right. How many students are in the row?",
                String.valueOf(total), List.of(String.valueOf(total + 1), String.valueOf(total + 2), String.valueOf(total - 1)),
                left + " + " + right + " − 1 = " + total + " (Asha is counted twice).", r);
    }

    private GeneratedMcq letterShift(Random r) {
        String[] words = {"CODE", "JAVA", "TEST", "DATA", "LOOP", "NODE", "BYTE", "GRID"};
        String w = words[r.nextInt(words.length)];
        String v = words[r.nextInt(words.length)];
        if (w.equals(v)) {
            return null;
        }
        int k = r.nextInt(3) + 1;
        return mcq("If " + w + " is written as " + shift(w, k) + ", how is " + v + " written in that code?", shift(v, k),
                List.of(shift(v, k + 1), shift(v, -k), new StringBuilder(shift(v, k)).reverse().toString()),
                "Each letter moves " + k + " place(s) forward in the alphabet.", r);
    }

    private GeneratedMcq direction(Random r) {
        int[][] triples = {{3, 4, 5}, {6, 8, 10}, {5, 12, 13}, {9, 12, 15}, {8, 15, 17}};
        int[] t = triples[r.nextInt(triples.length)];
        return mcq("Ravi walks " + t[0] + " km north and then " + t[1] + " km east. How far is he from the starting point?", t[2] + " km",
                List.of((t[0] + t[1]) + " km", (t[2] + 1) + " km", (t[1] - t[0]) + " km"),
                "The path forms a right angle: √(" + t[0] + "² + " + t[1] + "²) = " + t[2] + " km.", r);
    }

    // ---------------------------------------------------------------- verbal

    private GeneratedMcq word(Random r, boolean synonym) {
        String[] w = WORDS[r.nextInt(WORDS.length)];
        List<String> distractors = new ArrayList<>();
        while (distractors.size() < 3) {
            String[] other = WORDS[r.nextInt(WORDS.length)];
            String d = other[r.nextBoolean() ? 1 : 2];
            if (!other[0].equals(w[0]) && !d.equals(w[1]) && !d.equals(w[2]) && !distractors.contains(d)) {
                distractors.add(d);
            }
        }
        return synonym
                ? mcq("Choose the word closest in meaning to " + w[0] + ".", w[1], distractors, w[0] + " means " + w[1].toLowerCase(Locale.ROOT) + ".", r)
                : mcq("Choose the word opposite in meaning to " + w[0] + ".", w[2], distractors,
                w[0] + " means " + w[1].toLowerCase(Locale.ROOT) + "; the opposite is " + w[2].toLowerCase(Locale.ROOT) + ".", r);
    }

    // ---------------------------------------------------------------- technical

    private GeneratedMcq binary(Random r, int level) {
        int n = r.nextInt(level == 1 ? 30 : 120) + 5;
        String b = Integer.toBinaryString(n);
        return mcq("What is the decimal number " + n + " in binary?", b,
                List.of(Integer.toBinaryString(n + 1), Integer.toBinaryString(n - 1), Integer.toBinaryString(n ^ 2).equals(b) ? Integer.toBinaryString(n + 2) : Integer.toBinaryString(n ^ 2)),
                n + " = " + b + " in base 2.", r);
    }

    private GeneratedMcq loopComplexity(Random r) {
        String[][] cases = {
                {"for (i = 0; i < n; i++) sum += i;", "O(n)", "One loop over n elements."},
                {"for (i = 0; i < n; i++) for (j = 0; j < n; j++) count++;", "O(n²)", "Two nested loops of n iterations each."},
                {"for (i = 1; i < n; i = i * 2) count++;", "O(log n)", "i doubles each time, so the loop runs about log₂ n times."},
                {"for (i = 0; i < n; i++) for (j = 1; j < n; j = j * 2) count++;", "O(n log n)", "n outer iterations × log n inner iterations."},
                {"for (i = 0; i < n; i++) for (j = i; j < n; j++) count++;", "O(n²)", "n + (n−1) + ... + 1 = n(n+1)/2 iterations."}};
        String[] c = cases[r.nextInt(cases.length)];
        List<String> all = new ArrayList<>(List.of("O(n)", "O(n²)", "O(log n)", "O(n log n)", "O(1)"));
        all.remove(c[1]);
        Collections.shuffle(all, r);
        return mcq("What is the time complexity of: " + c[0], c[1], all.subList(0, 3), c[2], r);
    }

    private GeneratedMcq treeEdges(Random r) {
        int n = r.nextInt(40) + 5;
        return mcq("How many edges does a tree with " + n + " nodes have?", String.valueOf(n - 1),
                List.of(String.valueOf(n), String.valueOf(n + 1), String.valueOf(n * (n - 1) / 2)),
                "A tree with n nodes always has exactly n − 1 edges.", r);
    }

    private GeneratedMcq port(Random r) {
        String[][] ports = {{"HTTP", "80"}, {"HTTPS", "443"}, {"SSH", "22"}, {"DNS", "53"}, {"SMTP", "25"}, {"FTP (control)", "21"}};
        String[] p = ports[r.nextInt(ports.length)];
        List<String> wrong = new ArrayList<>();
        for (String[] o : ports) {
            if (!o[1].equals(p[1])) {
                wrong.add(o[1]);
            }
        }
        Collections.shuffle(wrong, r);
        return mcq("What is the default port number of " + p[0] + "?", p[1], wrong.subList(0, 3), p[0] + " uses port " + p[1] + " by default.", r);
    }

    private GeneratedMcq dataStructure(Random r) {
        String[][] facts = {
                {"Which data structure is used to implement recursion (function calls)?", "Stack", "Each call is pushed and popped in LIFO order."},
                {"Which data structure is used for breadth-first search?", "Queue", "BFS visits nodes level by level in FIFO order."},
                {"Which traversal of a binary search tree prints the keys in sorted order?", "Inorder", "Inorder visits left subtree, node, right subtree."},
                {"Which data structure gives O(1) average-time lookup by key?", "Hash table", "A good hash function maps keys to buckets directly."},
                {"Which data structure is best for repeatedly removing the minimum element?", "Min-heap", "A min-heap removes the minimum in O(log n)."}};
        String[] f = facts[r.nextInt(facts.length)];
        List<String> options = new ArrayList<>(List.of("Stack", "Queue", "Inorder", "Hash table", "Min-heap", "Linked list", "Preorder"));
        options.remove(f[1]);
        Collections.shuffle(options, r);
        return mcq(f[0], f[1], options.subList(0, 3), f[2], r);
    }

    // ---------------------------------------------------------------- helpers

    private static GeneratedMcq mcq(String question, String correct, List<String> wrong, String explanation, Random r) {
        Set<String> distinct = new LinkedHashSet<>();
        distinct.add(correct);
        for (String w : wrong) {
            if (w != null && !w.isBlank()) {
                distinct.add(w);
            }
        }
        if (distinct.size() < 4) {
            return null;
        }
        List<String> options = new ArrayList<>(distinct).subList(0, 4);
        options = new ArrayList<>(options);
        Collections.shuffle(options, r);
        return new GeneratedMcq(question, options, options.indexOf(correct), explanation);
    }

    private static String joinInts(List<Integer> list) {
        return String.join(", ", list.stream().map(String::valueOf).toList());
    }

    private static String shift(String w, int k) {
        StringBuilder sb = new StringBuilder();
        for (char c : w.toCharArray()) {
            sb.append((char) ('A' + Math.floorMod(c - 'A' + k, 26)));
        }
        return sb.toString();
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }
}
