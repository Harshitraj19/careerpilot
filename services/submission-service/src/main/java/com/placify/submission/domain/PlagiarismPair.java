package com.placify.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Two submissions that look too similar.
 */
@Entity
@Table(name = "plagiarism_pairs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlagiarismPair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_id", nullable = false)
    private Long checkId;

    @Column(name = "question_id", nullable = false, length = 36)
    private String questionId;

    @Column(name = "submission_a", nullable = false, length = 36)
    private String submissionA;

    @Column(name = "submission_b", nullable = false, length = 36)
    private String submissionB;

    @Column(name = "user_a", nullable = false, length = 36)
    private String userA;

    @Column(name = "user_b", nullable = false, length = 36)
    private String userB;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal similarity;
}
