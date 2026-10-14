package com.placify.placement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * College placement policy configured by the TPO (single row, id = 1).
 */
@Entity
@Table(name = "placement_policy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementPolicy {

    @Id
    private Long id;

    /** CTC (LPA) from which a company counts as Dream. */
    @Column(name = "dream_min_ctc", nullable = false, precision = 6, scale = 2)
    private BigDecimal dreamMinCtc;

    /** CTC (LPA) from which a company counts as Super-Dream. */
    @Column(name = "super_dream_min_ctc", nullable = false, precision = 6, scale = 2)
    private BigDecimal superDreamMinCtc;

    /** Once placed, a student may only apply to companies of a higher tier. */
    @Column(name = "one_offer_policy", nullable = false)
    private boolean oneOfferPolicy;

    /** Maximum number of active applications per student; null = unlimited. */
    @Column(name = "max_active_applications")
    private Integer maxActiveApplications;

    /** Students must attend the company's pre-placement talk before applying. */
    @Column(name = "ppt_mandatory", nullable = false)
    private boolean pptMandatory;

    /** Students need an uploaded resume to apply. */
    @Column(name = "resume_required", nullable = false)
    private boolean resumeRequired;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Job.Tier tierFor(BigDecimal ctcLpa) {
        if (ctcLpa == null) {
            return Job.Tier.NORMAL;
        }
        if (ctcLpa.compareTo(superDreamMinCtc) >= 0) {
            return Job.Tier.SUPER_DREAM;
        }
        if (ctcLpa.compareTo(dreamMinCtc) >= 0) {
            return Job.Tier.DREAM;
        }
        return Job.Tier.NORMAL;
    }
}
