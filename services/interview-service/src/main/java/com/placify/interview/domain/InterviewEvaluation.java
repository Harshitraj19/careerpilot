package com.placify.interview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A company's structured scorecard for one candidate in one interview round.
 * Saving it moves the candidate forward (or out) in the placement pipeline.
 */
@Entity
@Table(name = "interview_evaluations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewEvaluation {

    /** Values understood by the placement service's round results. */
    public enum Result { CLEARED, REJECTED, ABSENT, UNDER_REVIEW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "company_user_id", nullable = false, length = 36)
    private String companyUserId;

    @Column(name = "interviewer_name")
    private String interviewerName;

    @Column(nullable = false)
    private int technical;

    @Column(name = "problem_solving", nullable = false)
    private int problemSolving;

    @Column(nullable = false)
    private int communication;

    @Column(name = "culture_fit", nullable = false)
    private int cultureFit;

    /** 0-100, weighted from the four criteria. */
    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Result result;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    /** Whether the student may read the written feedback. */
    @Column(name = "share_feedback", nullable = false)
    private boolean shareFeedback;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Weighted overall score (technical 35, problem solving 30, communication 20, culture fit 15) on 0-100. */
    public static int overall(int technical, int problemSolving, int communication, int cultureFit) {
        double weighted = technical * 0.35 + problemSolving * 0.30 + communication * 0.20 + cultureFit * 0.15;
        return (int) Math.round(weighted * 10);
    }
}
