package com.placify.submission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Judge0 token for one (submission, test case) pair while it is being executed.
 */
@Entity
@Table(name = "submission_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionToken {

    public enum Status { pending, processing, completed }

    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "submission_id", nullable = false, length = 36)
    private String submissionId;

    @Column(name = "testcase_id", nullable = false, length = 36)
    private String testcaseId;

    @Column(nullable = false)
    private boolean hidden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
