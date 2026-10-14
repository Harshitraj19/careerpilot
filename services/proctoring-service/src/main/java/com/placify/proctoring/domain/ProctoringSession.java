package com.placify.proctoring.domain;

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

import java.time.LocalDateTime;

/**
 * Proctoring of one test attempt. Settings are copied from the test when the
 * session starts.
 */
@Entity
@Table(name = "proctoring_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProctoringSession {

    public enum Status { ACTIVE, ENDED, TERMINATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, unique = true)
    private Long attemptId;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(name = "test_owner_user_id", length = 36)
    private String testOwnerUserId;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "reference_photo_path", length = 500)
    private String referencePhotoPath;

    /** null = could not be checked (no profile photo / no face). */
    @Column(name = "identity_verified")
    private Boolean identityVerified;

    @Column(name = "identity_similarity")
    private Double identitySimilarity;

    @Column(name = "trust_score", nullable = false)
    private int trustScore;

    @Column(name = "violation_count", nullable = false)
    private int violationCount;

    @Column(name = "high_count", nullable = false)
    private int highCount;

    @Column(name = "medium_count", nullable = false)
    private int mediumCount;

    @Column(name = "low_count", nullable = false)
    private int lowCount;

    @Column(name = "frames_analysed", nullable = false)
    private int framesAnalysed;

    @Column(name = "max_violations", nullable = false)
    private int maxViolations;

    @Column(name = "auto_submit", nullable = false)
    private boolean autoSubmit;

    @Column(name = "camera_required", nullable = false)
    private boolean cameraRequired;

    @Column(name = "enabled_checks", length = 500)
    private String enabledChecks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status;

    @Column(name = "consent_at", nullable = false)
    private LocalDateTime consentAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    void onCreate() {
        startedAt = LocalDateTime.now();
    }

    public boolean checkEnabled(ViolationType type) {
        return enabledChecks == null || enabledChecks.isBlank() || ("," + enabledChecks + ",").contains("," + type.name() + ",");
    }
}
