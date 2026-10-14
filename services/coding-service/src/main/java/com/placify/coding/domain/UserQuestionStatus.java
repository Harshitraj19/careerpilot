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
 * Practice progress of a student on one problem.
 */
@Entity
@Table(name = "user_question_status")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestionStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "question_id", nullable = false, length = 36)
    private String questionId;

    @Column(nullable = false)
    private boolean solved;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "first_solved_at")
    private LocalDateTime firstSolvedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
}
