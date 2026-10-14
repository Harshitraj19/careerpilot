package com.placify.coding.domain;

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

import java.time.LocalDateTime;

/**
 * A student's practice rating (Elo) and counters.
 */
@Entity
@Table(name = "user_ratings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRating {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(length = 255)
    private String name;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private int solved;

    @Column(nullable = false)
    private int submissions;

    @Column(name = "easy_solved", nullable = false)
    private int easySolved;

    @Column(name = "medium_solved", nullable = false)
    private int mediumSolved;

    @Column(name = "hard_solved", nullable = false)
    private int hardSolved;

    @Column(name = "contests_joined", nullable = false)
    private int contestsJoined;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
