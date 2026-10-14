package com.placify.notification.domain;

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

/** A query or issue raised by a student for the placement office (help desk). */
@Entity
@Table(name = "student_queries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuery {

    public enum Status { PENDING, RESOLVED }

    public enum Category {
        PLACEMENT_DRIVE("Placement drive or job"),
        ELIGIBILITY("Eligibility"),
        TEST_OR_PROCTORING("Online test or proctoring"),
        INTERVIEW("Interview or slot"),
        OFFER_OR_CERTIFICATE("Offer or certificate"),
        PROFILE_OR_RESUME("Profile or resume"),
        TECHNICAL_ISSUE("Technical issue"),
        OTHER("Other");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_user_id", nullable = false, length = 36)
    private String studentUserId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_login", length = 100)
    private String studentLogin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Column(nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** True when the placement office replied last and is waiting for the student. */
    @Column(name = "awaiting_student", nullable = false)
    private boolean awaitingStudent;

    @Column(name = "resolved_by_name")
    private String resolvedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Reference shown to students and staff, e.g. "Q-000042". */
    public String reference() {
        return String.format("Q-%06d", id);
    }
}
