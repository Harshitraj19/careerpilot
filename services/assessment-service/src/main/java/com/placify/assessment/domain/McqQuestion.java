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

import java.time.LocalDateTime;

/**
 * Multiple-choice question (aptitude or technical). AI-generated questions
 * start unapproved and must be reviewed by the TPO.
 */
@Entity
@Table(name = "mcq_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McqQuestion {

    public enum Topic { QUANTITATIVE, LOGICAL, VERBAL, TECHNICAL }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public enum Source { MANUAL, AI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Topic topic;

    /** e.g. "Percentages", "Blood relations", "DBMS". */
    @Column(length = 100)
    private String subtopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    /** JSON array of the option texts (usually 4). */
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
