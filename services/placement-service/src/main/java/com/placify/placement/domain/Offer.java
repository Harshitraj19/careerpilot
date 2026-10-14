package com.placify.placement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An offer. On-campus offers are issued by the company to a selected candidate;
 * off-campus offers are reported by the student. Both are verified by the TPO.
 * <pre>
 * ISSUED -> ACCEPTED -> VERIFIED (placed)
 *        \-> DECLINED / EXPIRED          ACCEPTED -> REJECTED (TPO rejects the claim)
 * </pre>
 */
@Entity
@Table(name = "offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Offer {

    public enum Status { ISSUED, ACCEPTED, DECLINED, EXPIRED, VERIFIED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "company_user_id", length = 36)
    private String companyUserId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String role;

    @Column(name = "ctc_lpa", precision = 6, scale = 2)
    private BigDecimal ctcLpa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Job.Tier tier;

    @Column(name = "offer_letter_path", length = 500)
    private String offerLetterPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "off_campus", nullable = false)
    private boolean offCampus;

    @Column(name = "response_deadline")
    private LocalDateTime responseDeadline;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "verified_by", length = 36)
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
