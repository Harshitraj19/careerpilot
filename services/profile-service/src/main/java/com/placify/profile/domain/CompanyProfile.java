package com.placify.profile.domain;

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
 * Public profile of a recruiting company.
 */
@Entity
@Table(name = "company_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfile {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String about;

    private String industry;

    @Column(length = 500)
    private String website;

    private String location;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "logo_path", length = 500)
    private String logoPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
