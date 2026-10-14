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

/**
 * A question in a test's pool. Each student gets a random subset of the pool.
 * For MCQs refId is the MCQ id; for coding it is the coding-service question id.
 */
@Entity
@Table(name = "test_pool_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPoolItem {

    public enum Kind { MCQ, CODING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "ref_id", nullable = false, length = 36)
    private String refId;

    /** Marks for a coding problem (MCQs use the test's marks per MCQ). */
    @Column(nullable = false)
    private int points;
}
