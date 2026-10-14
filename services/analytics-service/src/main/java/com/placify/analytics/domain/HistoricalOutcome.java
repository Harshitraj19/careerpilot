package com.placify.analytics.domain;

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
 * One training example for the placement-chance model: a student's
 * preparation profile at the end of a season and whether they were placed.
 * Component values are on 0..100 (cgpa on 0..10).
 */
@Entity
@Table(name = "historical_outcomes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalOutcome {

    public static final String SYNTHETIC = "SYNTHETIC";
    public static final String RECORDED = "RECORDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String source;

    private Integer batch;

    @Column(nullable = false)
    private double cgpa;

    @Column(nullable = false)
    private int backlogs;

    @Column(nullable = false)
    private double coding;

    @Column(nullable = false)
    private double aptitude;

    @Column(nullable = false)
    private double ats;

    @Column(nullable = false)
    private double mock;

    @Column(nullable = false)
    private double contests;

    @Column(nullable = false)
    private boolean placed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
