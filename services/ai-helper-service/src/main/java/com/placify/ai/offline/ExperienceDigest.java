package com.placify.ai.offline;

import com.placify.ai.web.AiDtos.ExperienceSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Summarises seniors' interview experiences for one company without a language model (extractive). */
@Component
public class ExperienceDigest {

    private static final Map<String, List<String>> TOPICS = new LinkedHashMap<>();
    private static final Map<String, List<String>> ROUNDS = new LinkedHashMap<>();

    static {
        TOPICS.put("Arrays and strings", List.of("array", "string", "subarray", "substring", "two pointer", "sliding window"));
        TOPICS.put("Linked lists", List.of("linked list"));
        TOPICS.put("Trees and graphs", List.of("tree", "graph", "bfs", "dfs", "bst"));
        TOPICS.put("Dynamic programming", List.of("dynamic programming", " dp ", "knapsack", "memoiz"));
        TOPICS.put("Sorting and searching", List.of("sort", "binary search"));
        TOPICS.put("Hashing", List.of("hash", "hashmap"));
        TOPICS.put("OOP", List.of("oop", "object oriented", "inheritance", "polymorphism"));
        TOPICS.put("DBMS and SQL", List.of("sql", "dbms", "join", "normalization", "query"));
        TOPICS.put("Operating systems", List.of("operating system", " os ", "deadlock", "process", "thread", "paging"));
        TOPICS.put("Computer networks", List.of("network", "tcp", "http", "dns"));
        TOPICS.put("System design", List.of("system design", "design a", "scalab", "low level design"));
        TOPICS.put("Projects", List.of("project", "resume"));
        TOPICS.put("Aptitude and puzzles", List.of("aptitude", "puzzle", "quant", "logical", "reasoning"));
        TOPICS.put("Behavioural (HR)", List.of("hr round", "tell me about yourself", "strength", "weakness", "relocat", "why do you want"));
        ROUNDS.put("Online test", List.of("online test", "online assessment", "oa ", "coding test", "aptitude test", "written test"));
        ROUNDS.put("Group discussion", List.of("group discussion", " gd "));
        ROUNDS.put("Technical interview", List.of("technical round", "technical interview", "tech round", "technical 1", "technical"));
        ROUNDS.put("Managerial round", List.of("managerial", "manager round"));
        ROUNDS.put("HR interview", List.of("hr round", "hr interview", " hr "));
    }

    public ExperienceSummary summarise(String company, List<String> experiences) {
        String all = " " + String.join(" \n ", experiences).toLowerCase(Locale.ROOT) + " ";
        Map<String, Integer> counts = new LinkedHashMap<>();
        TOPICS.forEach((name, keys) -> {
            int n = 0;
            for (String e : experiences) {
                String lower = " " + e.toLowerCase(Locale.ROOT) + " ";
                if (keys.stream().anyMatch(lower::contains)) {
                    n++;
                }
            }
            if (n > 0) {
                counts.put(name, n);
            }
        });
        List<String> topics = counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).map(Map.Entry::getKey).limit(8).toList();

        List<String> pattern = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, List<String>> r : ROUNDS.entrySet()) {
            int first = r.getValue().stream().mapToInt(all::indexOf).filter(p -> p >= 0).min().orElse(-1);
            if (first >= 0) {
                positions.add(new int[] {first, i});
            }
            i++;
        }
        List<String> names = new ArrayList<>(ROUNDS.keySet());
        positions.sort((a, b) -> a[0] - b[0]);
        positions.forEach(p -> pattern.add(names.get(p[1])));

        int hard = count(all, "hard", "tough", "difficult", "challenging");
        int easy = count(all, "easy", "simple", "basic");
        String difficulty = hard > easy + 1 ? "HARD" : easy > hard + 1 ? "EASY" : "MEDIUM";

        Set<String> tips = new LinkedHashSet<>();
        for (String e : experiences) {
            for (String sentence : e.split("(?<=[.!?])\\s+|\\n")) {
                String s = sentence.strip();
                String lower = s.toLowerCase(Locale.ROOT);
                if (s.length() > 20 && s.length() < 220 && (lower.contains("prepare") || lower.contains("practi") || lower.contains("focus")
                        || lower.contains("make sure") || lower.contains("revise") || lower.contains("tip") || lower.contains("be ready")
                        || lower.contains("be clear") || lower.contains("don't") || lower.contains("do not"))) {
                    tips.add(s);
                }
            }
        }
        if (tips.isEmpty()) {
            tips.add("Revise the topics above and practise explaining your projects clearly.");
        }
        String summary = experiences.size() + " senior(s) shared their " + company + " interview experience. Most found the process "
                + difficulty.toLowerCase(Locale.ROOT) + "."
                + (topics.isEmpty() ? "" : " The most frequent topics were " + String.join(", ", topics.subList(0, Math.min(4, topics.size()))) + ".")
                + (pattern.isEmpty() ? "" : " The usual sequence was " + String.join(" -> ", pattern) + ".")
                + " Read the individual experiences below for the exact questions.";
        return new ExperienceSummary(summary, topics, pattern.isEmpty() ? null : String.join(" -> ", pattern), difficulty,
                tips.stream().limit(5).toList());
    }

    private static int count(String text, String... words) {
        int n = 0;
        for (String w : words) {
            int idx = text.indexOf(w);
            while (idx >= 0) {
                n++;
                idx = text.indexOf(w, idx + w.length());
            }
        }
        return n;
    }
}
