package com.placify.coding.domain;

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

import java.time.LocalDateTime;

/**
 * Per-problem state of a participant during a contest (for score and penalty).
 */
@Entity
@Table(name = "contest_problem_status")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContestProblemStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "question_id", nullable = false, length = 36)
    private String questionId;

    @Column(nullable = false)
    private boolean solved;

    @Column(name = "wrong_attempts", nullable = false)
    private int wrongAttempts;

    @Column(name = "solved_at")
    private LocalDateTime solvedAt;
}
