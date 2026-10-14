package com.placify.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Aptitude practice accuracy per student and topic.
 */
@Entity
@Table(name = "practice_stats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private McqQuestion.Topic topic;

    @Column(nullable = false)
    private int attempted;

    @Column(nullable = false)
    private int correct;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
