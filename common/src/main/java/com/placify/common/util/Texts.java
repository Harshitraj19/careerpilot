package com.placify.common.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Small text helpers used across services (skills are stored as comma lists).
 */
public final class Texts {

    private Texts() {
    }

    /** "Java, spring boot ,SQL" -> ["java", "spring boot", "sql"] (lower-case, unique, ordered). */
    public static Set<String> toSkillSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(csv.split("[,;\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<String> toList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static String join(java.util.Collection<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
