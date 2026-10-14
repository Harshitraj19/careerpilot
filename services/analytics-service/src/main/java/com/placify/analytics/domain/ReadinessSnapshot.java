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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Latest readiness score of a student, with its components (each already weighted). */
@Entity
@Table(name = "readiness_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessSnapshot {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private double coding;

    @Column(nullable = false)
    private double aptitude;

    @Column(nullable = false)
    private double ats;

    @Column(nullable = false)
    private double profile;

    @Column(nullable = false)
    private double mock;

    @Column(nullable = false)
    private double contests;

    @Column(precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(nullable = false)
    private int backlogs;

    @Column(length = 100)
    private String branch;

    private Integer batch;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
