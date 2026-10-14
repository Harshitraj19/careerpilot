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

import java.math.BigDecimal;

/**
 * A question as it appears in one attempt (order and option order are random
 * per student). The stored answer is always the ORIGINAL option index.
 */
@Entity
@Table(name = "attempt_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TestPoolItem.Kind kind;

    @Column(name = "ref_id", nullable = false, length = 36)
    private String refId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** JSON array mapping displayed position -> original option index, e.g. [2,0,3,1]. */
    @Column(name = "option_order", length = 100)
    private String optionOrder;

    @Column(name = "selected_index")
    private Integer selectedIndex;

    private Boolean correct;

    @Column(name = "max_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal maxMarks;

    @Column(precision = 7, scale = 2)
    private BigDecimal marks;

    @Column(name = "best_submission_id", length = 36)
    private String bestSubmissionId;

    @Column(name = "coding_passed")
    private Integer codingPassed;

    @Column(name = "coding_total")
    private Integer codingTotal;
}
