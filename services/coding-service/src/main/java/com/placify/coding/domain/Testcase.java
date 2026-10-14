package com.placify.coding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * Input / expected output pair. Hidden test cases are used for grading only and
 * their expected output is never sent to students.
 */
@Entity
@Table(name = "testcases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Testcase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "question_id", nullable = false, length = 36)
    private String questionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "time_limit", precision = 5, scale = 2)
    private BigDecimal timeLimit;

    @Column(name = "memory_limit", precision = 8, scale = 2)
    private BigDecimal memoryLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
