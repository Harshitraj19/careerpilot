package com.placify.submission.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Supported languages with their Judge0 CE language ids.
 */
public enum Language {
    C(50, "C (GCC 9.2)", "C (GCC)", "c"),
    CPP(54, "C++ (GCC 9.2)", "C++ (GCC)", "cpp"),
    JAVA(62, "Java (OpenJDK 13)", "Java", "java"),
    PYTHON(71, "Python 3.8", "Python 3", "py"),
    JAVASCRIPT(63, "JavaScript (Node 12)", "JavaScript (Node.js)", "js"),
    GO(60, "Go 1.13", "Go", "go"),
    RUST(73, "Rust 1.40", "Rust", "rs");

    private final int judge0Id;
    private final String label;
    private final String localLabel;
    private final String extension;

    Language(int judge0Id, String label, String localLabel, String extension) {
        this.judge0Id = judge0Id;
        this.label = label;
        this.localLabel = localLabel;
        this.extension = extension;
    }

    /** Label used when code runs on the local runner (whatever version is installed). */
    public String localLabel() {
        return localLabel;
    }

    public int judge0Id() {
        return judge0Id;
    }

    public String label() {
        return label;
    }

    public String extension() {
        return extension;
    }

    public static Optional<Language> byJudge0Id(int id) {
        return Arrays.stream(values()).filter(l -> l.judge0Id == id).findFirst();
    }
}
