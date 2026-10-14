package com.placify.placement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An interview time slot published by a company. One student per slot;
 * optimistic locking (@Version) prevents two students booking the same slot.
 */
@Entity
@Table(name = "interview_slots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "company_user_id", nullable = false, length = 36)
    private String companyUserId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 500)
    private String location;

    @Column(name = "booked_by", length = 36)
    private String bookedBy;

    @Column(name = "booked_at")
    private LocalDateTime bookedAt;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    @Version
    private Long version;

    public boolean isBooked() {
        return bookedBy != null;
    }
}
