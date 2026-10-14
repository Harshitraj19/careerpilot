package com.placify.coding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A coding problem. PRACTICE problems are open to all students; TEST_ONLY
 * problems are kept hidden for company/TPO tests.
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    public enum Difficulty {
        EASY(1000), MEDIUM(1400), HARD(1800);

        private final int defaultRating;

        Difficulty(int defaultRating) {
            this.defaultRating = defaultRating;
        }

        public int defaultRating() {
            return defaultRating;
        }
    }

    public enum Visibility { PRACTICE, TEST_ONLY }

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "input_format", columnDefinition = "TEXT")
    private String inputFormat;

    @Column(name = "output_format", columnDefinition = "TEXT")
    private String outputFormat;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Column(name = "sample_input", columnDefinition = "TEXT")
    private String sampleInput;

    @Column(name = "sample_output", columnDefinition = "TEXT")
    private String sampleOutput;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    /** Comma-separated topic tags, e.g. "arrays, two pointers". */
    @Column(length = 500)
    private String tags;

    /** Elo-style problem rating used for adaptive practice. */
    @Column(nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Visibility visibility;

    @Column(name = "time_limit", nullable = false, precision = 5, scale = 2)
    private BigDecimal timeLimit;

    @Column(name = "memory_limit", nullable = false, precision = 8, scale = 2)
    private BigDecimal memoryLimit;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "solved_count", nullable = false)
    private int solvedCount;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
