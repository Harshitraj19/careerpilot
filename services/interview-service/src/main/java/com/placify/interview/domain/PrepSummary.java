package com.placify.interview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Cached AI summary of all approved experiences of a company. */
@Entity
@Table(name = "prep_summaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrepSummary {

    @Id
    @Column(name = "company_key")
    private String companyKey;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "experience_count", nullable = false)
    private int experienceCount;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
