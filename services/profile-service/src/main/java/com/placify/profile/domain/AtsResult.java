package com.placify.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One ATS check of a student's resume against a job description.
 */
@Entity
@Table(name = "ats_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "company_name")
    private String companyName;

    @Column(nullable = false)
    private int score;

    @Column(name = "keyword_score", nullable = false)
    private int keywordScore;

    @Column(name = "semantic_score", nullable = false)
    private int semanticScore;

    @Column(name = "content_score", nullable = false)
    private int contentScore;

    @Column(name = "structure_score", nullable = false)
    private int structureScore;

    @Column(name = "impact_score", nullable = false)
    private int impactScore;

    @Column(name = "parseability_score", nullable = false)
    private int parseabilityScore;

    @Column(name = "matched_keywords", columnDefinition = "TEXT")
    private String matchedKeywords;

    @Column(name = "missing_keywords", columnDefinition = "TEXT")
    private String missingKeywords;

    /** Rule-based tips joined by newlines. */
    @Column(columnDefinition = "TEXT")
    private String tips;

    /** JSON from the AI feedback (strengths, weaknesses, suggestions), when requested. */
    @Column(name = "ai_feedback_json", columnDefinition = "TEXT")
    private String aiFeedbackJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
