package com.placify.placement.domain;

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
import java.time.LocalDateTime;

/**
 * One selection round of a job (aptitude test, coding test, interviews ...).
 * Test rounds can be linked to an assessment so results are applied automatically.
 */
@Entity
@Table(name = "rounds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Round {

    public enum Type {
        APTITUDE_TEST, CODING_TEST, TECHNICAL_INTERVIEW, HR_INTERVIEW, GROUP_DISCUSSION, OTHER;

        public boolean isTest() {
            return this == APTITUDE_TEST || this == CODING_TEST;
        }

        public boolean isInterview() {
            return this == TECHNICAL_INTERVIEW || this == HR_INTERVIEW;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_type", nullable = false, length = 30)
    private Type type;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(length = 2000)
    private String description;

    /** Assessment test used for this round (assessment-service). */
    @Column(name = "assessment_test_id")
    private Long assessmentTestId;

    @Column(name = "cutoff_percent", precision = 5, scale = 2)
    private BigDecimal cutoffPercent;
}
