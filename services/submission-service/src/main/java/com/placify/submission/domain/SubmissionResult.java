package com.placify.submission.domain;

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
 * Verdict of one test case of a submission.
 */
@Entity
@Table(name = "submission_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResult {

    public enum Verdict { accepted, wrong_answer, time_limit_exceeded, memory_limit_exceeded, runtime_error, compilation_error }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false, length = 36)
    private String submissionId;

    @Column(name = "testcase_id", nullable = false, length = 36)
    private String testcaseId;

    @Column(nullable = false)
    private boolean hidden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Verdict status;

    @Column(name = "execution_time", precision = 8, scale = 3)
    private BigDecimal executionTime;

    @Column(name = "memory_used", precision = 10, scale = 2)
    private BigDecimal memoryUsed;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
