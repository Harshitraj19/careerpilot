package com.placify.coding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Weekly coding contest.
 */
@Entity
@Table(name = "contests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contest {

    public enum Phase { UPCOMING, RUNNING, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    /** Badges awarded and leaderboard frozen. */
    @Column(nullable = false)
    private boolean finalized;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Phase phase() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime)) {
            return Phase.UPCOMING;
        }
        return now.isBefore(endTime) ? Phase.RUNNING : Phase.ENDED;
    }
}
