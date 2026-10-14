package com.placify.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * All AI features, written in Java: Spring AI for the language-model features
 * (tutor, code review, mock interview, question generation, resume parsing,
 * summaries) and DJL for sentence embeddings (semantic similarity).
 */
@SpringBootApplication(scanBasePackages = "com.placify")
public class AiHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiHelperApplication.class, args);
    }
}
