package com.placify.ai.offline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Interview questions with the key points (and keywords) a strong answer covers; {@code offline/interview-bank.json}. */
@Component
public class InterviewBank {

    public record KeyPoint(String text, List<String> keywords) {
    }

    public record BankQuestion(String type, String topic, String difficulty, String question, String hint, List<KeyPoint> points) {

        public boolean isBehavioural() {
            return "HR".equals(type) || "PROJECT".equals(type);
        }

        public int level() {
            return switch (difficulty == null ? "" : difficulty.toUpperCase(Locale.ROOT)) {
                case "EASY" -> 1;
                case "HARD" -> 3;
                default -> 2;
            };
        }
    }

    private final List<BankQuestion> questions;

    public InterviewBank(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("offline/interview-bank.json").getInputStream()) {
            this.questions = List.copyOf(mapper.readValue(in, new TypeReference<List<BankQuestion>>() {
            }));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not load the interview question bank", ex);
        }
    }

    public List<BankQuestion> all() {
        return questions;
    }

    public Optional<BankQuestion> find(String questionText) {
        if (questionText == null) {
            return Optional.empty();
        }
        String q = questionText.trim();
        return questions.stream().filter(b -> b.question().equalsIgnoreCase(q)).findFirst();
    }
}
