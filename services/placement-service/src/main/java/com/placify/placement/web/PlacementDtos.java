package com.placify.placement.web;

import com.placify.placement.domain.Application;
import com.placify.placement.domain.CalendarEvent;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Offer;
import com.placify.placement.domain.Round;
import com.placify.placement.domain.RoundResult;
import com.placify.placement.rules.RuleCheck;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request and response bodies of the placement API.
 */
public final class PlacementDtos {

    private PlacementDtos() {
    }

    // ------------------------------------------------------------------ jobs

    public record RoundRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull Round.Type type,
            LocalDateTime scheduledAt,
            @Size(max = 2000) String description,
            @DecimalMin("0") @DecimalMax("100") BigDecimal cutoffPercent) {
    }

    /** One line of a job's salary breakdown. */
    public record SalaryComponent(
            @NotBlank @Size(max = 120) String component,
            @Size(max = 60) String amount,
            @Size(max = 255) String note) {
    }

    public record JobRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 20000) String description,
            List<@Size(max = 60) String> requiredSkills,
            @Size(max = 255) String location,
            @NotNull Job.Type jobType,
            @DecimalMin("0") @DecimalMax("9999") BigDecimal ctcLpa,
            @Min(0) Integer stipend,
            @DecimalMin("0") @DecimalMax("10") BigDecimal minCgpa,
            List<@Size(max = 100) String> allowedBranches,
            @Min(0) @Max(50) Integer maxBacklogs,
            Integer eligibleBatch,
            @DecimalMin("0") @DecimalMax("100") BigDecimal minTenth,
            @DecimalMin("0") @DecimalMax("100") BigDecimal minTwelfth,
            @Future LocalDateTime applicationDeadline,
            List<@Valid RoundRequest> rounds,
            List<@Valid SalaryComponent> ctcBreakdown) {
    }

    public record RoundView(Long id, int sequence, String name, Round.Type type, LocalDateTime scheduledAt,
                            String description, Long assessmentTestId, BigDecimal cutoffPercent) {

        public static RoundView of(Round r) {
            return new RoundView(r.getId(), r.getSequence(), r.getName(), r.getType(), r.getScheduledAt(),
                    r.getDescription(), r.getAssessmentTestId(), r.getCutoffPercent());
        }
    }

    public record JobView(
            Long id,
            String companyUserId,
            String companyName,
            String title,
            String description,
            List<String> requiredSkills,
            String location,
            Job.Type jobType,
            BigDecimal ctcLpa,
            Integer stipend,
            List<SalaryComponent> ctcBreakdown,
            Job.Tier tier,
            Job.Status status,
            BigDecimal minCgpa,
            List<String> allowedBranches,
            Integer maxBacklogs,
            Integer eligibleBatch,
            BigDecimal minTenth,
            BigDecimal minTwelfth,
            LocalDateTime applicationDeadline,
            String reviewNote,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            List<RoundView> rounds,
            Long applicantCount,
            // personalised fields for students
            Boolean eligible,
            List<String> notEligibleReasons,
            Application.Status myApplicationStatus,
            Integer matchPercent) {
    }

    public record PublishRequest(Job.Tier tier) {
    }

    public record NoteRequest(@Size(max = 1000) String note) {
    }

    public record EligibilityView(boolean eligible, List<RuleCheck> checks) {
    }

    // ------------------------------------------------------------------ applications

    public record ApplicationView(
            Long id,
            Long jobId,
            String jobTitle,
            String companyName,
            String studentUserId,
            String studentName,
            String regNo,
            String branch,
            Application.Status status,
            Long currentRoundId,
            String currentRoundName,
            Integer matchScore,
            Integer rankScore,
            String statusNote,
            LocalDateTime appliedAt,
            LocalDateTime updatedAt) {
    }

    public record ApplicantView(
            ApplicationView application,
            BigDecimal cgpa,
            int backlogs,
            List<String> skills,
            Integer atsScore,
            Integer readinessScore,
            boolean hasResume) {
    }

    public record TimelineStep(
            Long roundId,
            int sequence,
            String name,
            Round.Type type,
            LocalDateTime scheduledAt,
            String status,
            BigDecimal score,
            String remarks,
            LocalDateTime updatedAt,
            Long assessmentTestId,
            SlotView bookedSlot) {
    }

    public record Timeline(ApplicationView application, List<TimelineStep> steps) {
    }

    public record IdsRequest(@NotEmpty List<Long> applicationIds) {
    }

    public record RejectRequest(@NotEmpty List<Long> applicationIds, @Size(max = 1000) String reason) {
    }

    public record ResultUpdate(
            @NotEmpty List<Long> applicationIds,
            @NotNull RoundResult.Status status,
            @DecimalMin("0") @DecimalMax("100") BigDecimal score,
            @Size(max = 2000) String remarks) {
    }

    public record RoundResultView(
            Long id,
            Long roundId,
            Long applicationId,
            String studentUserId,
            String studentName,
            String regNo,
            RoundResult.Status status,
            BigDecimal score,
            String remarks,
            LocalDateTime updatedAt) {
    }

    public record LinkTestRequest(@NotNull Long assessmentTestId) {
    }

    // ------------------------------------------------------------------ policy

    public record PolicyUpdate(
            @NotNull @DecimalMin("0") BigDecimal dreamMinCtc,
            @NotNull @DecimalMin("0") BigDecimal superDreamMinCtc,
            boolean oneOfferPolicy,
            @Min(1) Integer maxActiveApplications,
            boolean pptMandatory,
            boolean resumeRequired) {
    }

    // ------------------------------------------------------------------ events

    public record EventRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull CalendarEvent.Type type,
            @Size(max = 2000) String description,
            Long jobId,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @Size(max = 500) String location,
            boolean generateAttendanceCode) {
    }

    public record EventView(
            Long id,
            String title,
            CalendarEvent.Type type,
            String description,
            Long jobId,
            String companyName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String location,
            String attendanceCode,
            Boolean attended,
            boolean canEdit) {
    }

    public record AttendanceRequest(@NotEmpty List<String> studentUserIds) {
    }

    public record CheckInRequest(@NotBlank String code) {
    }

    // ------------------------------------------------------------------ slots

    public record SlotGenerateRequest(
            @NotNull Long roundId,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @Min(5) @Max(240) int durationMinutes,
            @Min(0) @Max(120) int gapMinutes,
            @Size(max = 500) String location) {
    }

    public record SlotView(
            Long id,
            Long jobId,
            Long roundId,
            String roundName,
            String companyName,
            String jobTitle,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String location,
            boolean booked,
            boolean bookedByMe,
            String bookedByName) {
    }

    // ------------------------------------------------------------------ offers

    public record OfferView(
            Long id,
            Long applicationId,
            Long jobId,
            String studentUserId,
            String studentName,
            String regNo,
            String companyName,
            String role,
            BigDecimal ctcLpa,
            Job.Tier tier,
            Offer.Status status,
            boolean offCampus,
            LocalDateTime responseDeadline,
            LocalDateTime respondedAt,
            LocalDateTime verifiedAt,
            String remarks,
            boolean hasLetter,
            Long certificateId,
            LocalDateTime createdAt) {
    }

    public record CertificateView(
            Long id,
            String verificationCode,
            String studentName,
            String regNo,
            String branch,
            String companyName,
            String role,
            BigDecimal ctcLpa,
            LocalDateTime issuedAt,
            boolean revoked) {
    }

    public record VerificationResult(boolean valid, String message, CertificateView certificate) {
    }
}
