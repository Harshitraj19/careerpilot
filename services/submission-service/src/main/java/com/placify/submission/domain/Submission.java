package com.placify.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A graded code submission (all test cases). Practice, contest and test
 * submissions share the same engine; {@link Mode} says who consumes the result.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    public enum Status {
        pending, running, accepted, wrong_answer, time_limit_exceeded, memory_limit_exceeded, runtime_error, compilation_error;

        public boolean isFinal() {
            return this != pending && this != running;
        }
    }

    public enum Mode { PRACTICE, CONTEST, ASSESSMENT }

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "question_id", nullable = false, length = 36)
    private String questionId;

    @Column(name = "language_id", nullable = false)
    private Integer languageId;

    @Column(name = "source_code", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Mode mode;

    @Column(name = "contest_id")
    private Long contestId;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "testcases_passed", nullable = false)
    private int testcasesPassed;

    @Column(name = "testcases_failed", nullable = false)
    private int testcasesFailed;

    @Column(name = "total_testcases", nullable = false)
    private int totalTestcases;

    @Column(name = "execution_time", precision = 8, scale = 3)
    private BigDecimal executionTime;

    @Column(name = "memory_used", precision = 10, scale = 2)
    private BigDecimal memoryUsed;

    @Column(nullable = false)
    private int score;

    /** Secret part of the Judge0 callback URL, so callbacks cannot be faked. */
    @Column(name = "callback_secret", length = 64)
    private String callbackSecret;

    @Column(name = "compile_output", columnDefinition = "TEXT")
    private String compileOutput;

    @Column(name = "ai_review", columnDefinition = "TEXT")
    private String aiReview;

    @Column(name = "event_published", nullable = false)
    private boolean eventPublished;

    @Column(name = "submission_time", nullable = false, updatable = false)
    private LocalDateTime submissionTime;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = Status.pending;
        }
        submissionTime = LocalDateTime.now();
    }
}
