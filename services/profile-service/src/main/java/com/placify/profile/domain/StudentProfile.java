package com.placify.profile.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Academic and professional profile of a student. Academic fields (reg. no,
 * branch, batch, CGPA) come from the TPO import; the student fills the rest.
 */
@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "reg_no", nullable = false, length = 30)
    private String regNo;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 320)
    private String email;

    private String phone;

    @Column(nullable = false, length = 100)
    private String branch;

    @Column(nullable = false)
    private Integer batch;

    @Column(precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "tenth_percentage", precision = 5, scale = 2)
    private BigDecimal tenthPercentage;

    @Column(name = "twelfth_percentage", precision = 5, scale = 2)
    private BigDecimal twelfthPercentage;

    @Column(nullable = false)
    private int backlogs;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String certifications;

    /** JSON array of {title, description, techStack, link}. */
    @Column(name = "projects_json", columnDefinition = "TEXT")
    private String projectsJson;

    @Column(columnDefinition = "TEXT")
    private String internships;

    @Column(columnDefinition = "TEXT")
    private String achievements;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "resume_path", length = 500)
    private String resumePath;

    @Column(name = "resume_original_name")
    private String resumeOriginalName;

    @Column(name = "resume_uploaded_at")
    private LocalDateTime resumeUploadedAt;

    /** Plain text extracted from the resume PDF (used for ATS and matching). */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "resume_text", columnDefinition = "MEDIUMTEXT")
    private String resumeText;

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
