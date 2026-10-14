package com.placify.notification.domain;

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

/** One message in a query's conversation, from the student or the placement office. */
@Entity
@Table(name = "query_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_id", nullable = false)
    private Long queryId;

    @Column(name = "author_user_id", nullable = false, length = 36)
    private String authorUserId;

    @Column(name = "author_name")
    private String authorName;

    /** STUDENT, TPO or ADMIN. */
    @Column(name = "author_role", nullable = false, length = 20)
    private String authorRole;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
