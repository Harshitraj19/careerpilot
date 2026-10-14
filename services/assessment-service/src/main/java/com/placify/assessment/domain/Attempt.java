package com.placify.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/**
 * One student's attempt at a test.
 */
@Entity
@Table(name = "attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attempt {

    public enum Status {
        IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED, TERMINATED;

        public boolean isFinished() {
            return this != IN_PROGRESS;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_login", length = 64)
    private String studentLogin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "mcq_score", precision = 7, scale = 2)
    private BigDecimal mcqScore;

    @Column(name = "coding_score", precision = 7, scale = 2)
    private BigDecimal codingScore;

    @Column(name = "total_score", precision = 7, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "max_score", precision = 7, scale = 2)
    private BigDecimal maxScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal percent;

    private Boolean passed;

    @Column(name = "trust_score")
    private Integer trustScore;

    @Column(name = "proctoring_flagged", nullable = false)
    private boolean proctoringFlagged;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
