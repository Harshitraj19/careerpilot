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
 * One plagiarism run over the submissions of a test or a contest.
 */
@Entity
@Table(name = "plagiarism_checks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlagiarismCheck {

    public enum Scope { ASSESSMENT, CONTEST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Scope scope;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal threshold;

    @Column(name = "submissions_compared", nullable = false)
    private int submissionsCompared;

    @Column(name = "pairs_flagged", nullable = false)
    private int pairsFlagged;

    /** Which engine produced the result (JPlag or the built-in winnowing fallback). */
    @Column(length = 60)
    private String engine;

    @Column(name = "requested_by", nullable = false, length = 36)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
