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

import java.time.LocalDateTime;

/**
 * Placement calendar entry: pre-placement talk, drive, test, interview or other.
 * A pre-placement talk (PPT) can have an attendance code students enter to check in.
 */
@Entity
@Table(name = "calendar_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    public enum Type { PRE_PLACEMENT_TALK, DRIVE, TEST, INTERVIEW, WORKSHOP, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type type;

    @Column(length = 2000)
    private String description;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(length = 500)
    private String location;

    @Column(name = "attendance_code", length = 12)
    private String attendanceCode;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
