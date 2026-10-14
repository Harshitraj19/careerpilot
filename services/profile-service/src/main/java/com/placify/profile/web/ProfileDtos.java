package com.placify.profile.web;

import com.placify.profile.domain.AtsResult;
import com.placify.profile.domain.CompanyProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProjectItem(
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Size(max = 300) String techStack,
            @Size(max = 500) String link) {
    }

    /** Fields a student can edit. Academic fields (reg. no, branch, batch, CGPA) are managed by the TPO. */
    public record StudentProfileUpdate(
            @Pattern(regexp = "^[0-9+\\-\\s]{0,20}$", message = "Invalid phone number") String phone,
            @DecimalMin("0") @DecimalMax("100") BigDecimal tenthPercentage,
            @DecimalMin("0") @DecimalMax("100") BigDecimal twelfthPercentage,
            @Min(0) @Max(50) Integer backlogs,
            List<@Size(max = 60) String> skills,
            List<@Size(max = 200) String> certifications,
            List<@Valid ProjectItem> projects,
            @Size(max = 4000) String internships,
            @Size(max = 4000) String achievements,
            @Size(max = 2000) String summary,
            @Size(max = 500) String githubUrl,
            @Size(max = 500) String linkedinUrl,
            @Size(max = 500) String portfolioUrl) {
    }

    /** Academic data changes made by the TPO. */
    public record AcademicUpdate(
            @Size(max = 100) String branch,
            Integer batch,
            @DecimalMin("0") @DecimalMax("10") BigDecimal cgpa,
            @Min(0) @Max(50) Integer backlogs) {
    }

    public record StudentProfileView(
            String userId,
            String regNo,
            String name,
            String email,
            String phone,
            String branch,
            Integer batch,
            BigDecimal cgpa,
            BigDecimal tenthPercentage,
            BigDecimal twelfthPercentage,
            int backlogs,
            List<String> skills,
            List<String> certifications,
            List<ProjectItem> projects,
            String internships,
            String achievements,
            String summary,
            String githubUrl,
            String linkedinUrl,
            String portfolioUrl,
            boolean hasPhoto,
            boolean hasResume,
            String resumeOriginalName,
            LocalDateTime resumeUploadedAt,
            Completeness completeness,
            LocalDateTime updatedAt) {
    }

    public record Completeness(int percent, List<String> missing) {
    }

    /** Compact view used by other services (eligibility, matching, analytics). */
    public record StudentSummary(
            String userId,
            String regNo,
            String name,
            String email,
            String branch,
            Integer batch,
            BigDecimal cgpa,
            BigDecimal tenthPercentage,
            BigDecimal twelfthPercentage,
            int backlogs,
            List<String> skills,
            int completeness,
            boolean hasResume,
            boolean hasPhoto,
            Integer latestAtsScore,
            Integer bestAtsScore) {
    }

    public record CompanyProfileUpdate(
            @Size(max = 255) String companyName,
            @Size(max = 5000) String about,
            @Size(max = 255) String industry,
            @Size(max = 500) String website,
            @Size(max = 255) String location,
            @Min(1) Integer employeeCount) {
    }

    public record CompanyProfileView(
            String userId,
            String companyName,
            String email,
            String about,
            String industry,
            String website,
            String location,
            Integer employeeCount,
            boolean hasLogo) {

        public static CompanyProfileView of(CompanyProfile c) {
            return new CompanyProfileView(c.getUserId(), c.getCompanyName(), c.getEmail(), c.getAbout(),
                    c.getIndustry(), c.getWebsite(), c.getLocation(), c.getEmployeeCount(), c.getLogoPath() != null);
        }
    }

    public record AtsRequest(
            @Size(max = 255) String jobTitle,
            @Size(max = 255) String companyName,
            @NotBlank @Size(min = 50, max = 20000, message = "Paste the full job description (at least 50 characters)") String jobDescription,
            boolean useAi) {
    }

    public record AtsView(
            Long id,
            String jobTitle,
            String companyName,
            int score,
            Map<String, Integer> breakdown,
            List<String> matchedKeywords,
            List<String> missingKeywords,
            List<String> tips,
            Object aiFeedback,
            LocalDateTime createdAt) {

        public static AtsView of(AtsResult r, Object aiFeedback) {
            Map<String, Integer> breakdown = new java.util.LinkedHashMap<>();
            breakdown.put("keywordMatch", r.getKeywordScore());
            breakdown.put("semanticSimilarity", r.getSemanticScore());
            breakdown.put("contentSimilarity", r.getContentScore());
            breakdown.put("structure", r.getStructureScore());
            breakdown.put("impact", r.getImpactScore());
            breakdown.put("parseability", r.getParseabilityScore());
            return new AtsView(r.getId(), r.getJobTitle(), r.getCompanyName(), r.getScore(), breakdown,
                    split(r.getMatchedKeywords()), split(r.getMissingKeywords()),
                    r.getTips() == null ? List.of() : Arrays.asList(r.getTips().split("\n")),
                    aiFeedback, r.getCreatedAt());
        }

        private static List<String> split(String csv) {
            if (csv == null || csv.isBlank()) {
                return List.of();
            }
            return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record AtsStats(long checks, Integer latest, Integer best, Double average) {
    }

    public record IdsRequest(List<String> ids) {
    }
}
