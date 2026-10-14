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

/** A verified placement (from placement events), kept for long-term statistics. */
@Entity
@Table(name = "placement_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "ctc_lpa", precision = 6, scale = 2)
    private BigDecimal ctcLpa;

    @Column(length = 20)
    private String tier;

    @Column(name = "placed_at", nullable = false)
    private LocalDateTime placedAt;
}
