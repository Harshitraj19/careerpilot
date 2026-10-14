package com.placify.placement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A job or internship posted by a company. It becomes visible to students only
 * after the TPO publishes it.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    public enum Status { PENDING_APPROVAL, OPEN, CLOSED, REJECTED }

    public enum Tier { NORMAL, DREAM, SUPER_DREAM }

    public enum Type { FULL_TIME, INTERNSHIP, INTERNSHIP_WITH_PPO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_user_id", nullable = false, length = 36)
    private String companyUserId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Comma-separated required skills. */
    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private Type jobType;

    @Column(name = "ctc_lpa", precision = 6, scale = 2)
    private BigDecimal ctcLpa;

    /** Monthly stipend for internships. */
    private Integer stipend;

    /** Salary breakdown as JSON: [{"component": "Base pay", "amount": "₹9,00,000", "note": "per annum"}]. */
    @Column(name = "ctc_breakdown", columnDefinition = "TEXT")
    private String ctcBreakdown;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    // ---- eligibility criteria
    @Column(name = "min_cgpa", precision = 4, scale = 2)
    private BigDecimal minCgpa;

    /** Comma-separated branches; empty means all branches. */
    @Column(name = "allowed_branches", columnDefinition = "TEXT")
    private String allowedBranches;

    @Column(name = "max_backlogs")
    private Integer maxBacklogs;

    @Column(name = "eligible_batch")
    private Integer eligibleBatch;

    @Column(name = "min_tenth", precision = 5, scale = 2)
    private BigDecimal minTenth;

    @Column(name = "min_twelfth", precision = 5, scale = 2)
    private BigDecimal minTwelfth;

    @Column(name = "application_deadline")
    private LocalDateTime applicationDeadline;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isAcceptingApplications() {
        return status == Status.OPEN
                && (applicationDeadline == null || applicationDeadline.isAfter(LocalDateTime.now()));
    }
}
