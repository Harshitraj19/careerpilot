package com.placify.interview.domain;

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
import java.util.Locale;

/** An interview experience written by a student; visible to everyone after TPO approval. */
@Entity
@Table(name = "interview_experiences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewExperience {

    public enum Status { PENDING, APPROVED, REJECTED }

    public enum Outcome { SELECTED, REJECTED, WAITLISTED }

    public enum Difficulty { EASY, MEDIUM, HARD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_user_id", nullable = false, length = 36)
    private String authorUserId;

    @Column(name = "author_name")
    private String authorName;

    @Column(nullable = false)
    private boolean anonymous;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    /** Normalised company name used for grouping ("Tata Consultancy Services " -> "tata consultancy services"). */
    @Column(name = "company_key", nullable = false)
    private String companyKey;

    @Column(name = "job_role", nullable = false)
    private String jobRole;

    @Column(name = "interview_year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Outcome result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "rounds_summary", columnDefinition = "TEXT")
    private String roundsSummary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "questions_asked", columnDefinition = "TEXT")
    private String questionsAsked;

    @Column(columnDefinition = "TEXT")
    private String tips;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    @Column(nullable = false)
    private int upvotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public static String keyOf(String companyName) {
        return companyName == null ? "" : companyName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
