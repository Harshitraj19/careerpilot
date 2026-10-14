package com.placify.proctoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One recorded violation with its evidence snapshot (camera violations).
 */
@Entity
@Table(name = "proctoring_violations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {

    public enum Source { CAMERA, BROWSER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ViolationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ViolationType.Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(length = 500)
    private String detail;

    @Column(name = "snapshot_path", length = 500)
    private String snapshotPath;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
