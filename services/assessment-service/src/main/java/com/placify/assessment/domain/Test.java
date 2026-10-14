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
 * A timed test. When linked to a job round, only candidates waiting in that
 * round can take it and the result moves them forward automatically.
 * Proctoring settings are chosen per test by its owner.
 */
@Entity
@Table(name = "tests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Test {

    public enum Type { APTITUDE, CODING, MIXED }

    public enum Status { DRAFT, PUBLISHED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", nullable = false, length = 10)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "owner_user_id", nullable = false, length = 36)
    private String ownerUserId;

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "round_id")
    private Long roundId;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;

    @Column(name = "cutoff_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal cutoffPercent;

    /** How many MCQs each student gets from the pool. */
    @Column(name = "mcq_count", nullable = false)
    private int mcqCount;

    @Column(name = "marks_per_mcq", nullable = false)
    private int marksPerMcq;

    /** How many coding problems each student gets from the pool. */
    @Column(name = "coding_count", nullable = false)
    private int codingCount;

    /** Different question set and order for every student. */
    @Column(nullable = false)
    private boolean randomize;

    // ---- proctoring
    @Column(nullable = false)
    private boolean proctored;

    @Column(name = "camera_required", nullable = false)
    private boolean cameraRequired;

    @Column(name = "max_violations", nullable = false)
    private int maxViolations;

    @Column(name = "auto_submit_on_violations", nullable = false)
    private boolean autoSubmitOnViolations;

    /** Comma-separated enabled checks, e.g. "NO_FACE,MULTIPLE_FACES,PHONE,TAB_SWITCH". */
    @Column(name = "proctoring_checks", length = 500)
    private String proctoringChecks;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isOpenNow() {
        LocalDateTime now = LocalDateTime.now();
        return status == Status.PUBLISHED && !now.isBefore(windowStart) && now.isBefore(windowEnd);
    }
}
