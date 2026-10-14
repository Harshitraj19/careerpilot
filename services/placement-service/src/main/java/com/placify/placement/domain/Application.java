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

import java.time.LocalDateTime;

/**
 * A student's application to a job and where it stands in the selection process.
 * <pre>
 * APPLIED -> SHORTLISTED -> IN_PROCESS (round by round) -> SELECTED
 *        \-> REJECTED (at any point)      WITHDRAWN (by the student, before selection)
 * </pre>
 */
@Entity
@Table(name = "applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    public enum Status {
        APPLIED, SHORTLISTED, IN_PROCESS, SELECTED, REJECTED, WITHDRAWN;

        public boolean isActive() {
            return this == APPLIED || this == SHORTLISTED || this == IN_PROCESS;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "reg_no", length = 30)
    private String regNo;

    @Column(length = 100)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "current_round_id")
    private Long currentRoundId;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "rank_score")
    private Integer rankScore;

    @Column(name = "status_note", length = 1000)
    private String statusNote;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        appliedAt = LocalDateTime.now();
        updatedAt = appliedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
