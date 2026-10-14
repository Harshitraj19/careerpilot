package com.placify.proctoring.web;

import com.placify.proctoring.domain.ProctoringSession;
import com.placify.proctoring.domain.Violation;
import com.placify.proctoring.domain.ViolationType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ProctoringDtos {

    private ProctoringDtos() {
    }

    /** referencePhoto: data URL or base64 JPEG captured by the webcam at the start. */
    public record StartRequest(
            @NotNull Long attemptId,
            @AssertTrue(message = "You must agree to camera monitoring to take this test") boolean consent,
            @Size(max = 3_000_000) String referencePhoto) {
    }

    public record FrameRequest(@NotBlank @Size(max = 3_000_000) String image) {
    }

    public record BrowserEvent(@NotNull ViolationType type, @Size(max = 300) String detail) {
    }

    public record SessionStatus(
            Long sessionId,
            Long attemptId,
            ProctoringSession.Status status,
            int violationCount,
            int maxViolations,
            boolean cameraRequired,
            List<String> enabledChecks,
            Boolean identityVerified,
            List<WarningView> recentWarnings,
            boolean cameraAnalysisAvailable,
            boolean phoneDetectionAvailable) {
    }

    public record WarningView(Long id, ViolationType type, ViolationType.Severity severity, String message, LocalDateTime time) {

        public static WarningView of(Violation v) {
            return new WarningView(v.getId(), v.getType(), v.getSeverity(), v.getType().message(), v.getOccurredAt());
        }
    }

    public record ViolationView(Long id, ViolationType type, ViolationType.Severity severity, Violation.Source source,
                                String detail, boolean hasSnapshot, LocalDateTime occurredAt) {

        public static ViolationView of(Violation v) {
            return new ViolationView(v.getId(), v.getType(), v.getSeverity(), v.getSource(), v.getDetail(),
                    v.getSnapshotPath() != null, v.getOccurredAt());
        }
    }

    public record Report(
            Long sessionId,
            Long attemptId,
            Long testId,
            String studentUserId,
            ProctoringSession.Status status,
            int trustScore,
            boolean flagged,
            String flagReason,
            Boolean identityVerified,
            Double identitySimilarity,
            String identityMethod,
            int framesAnalysed,
            int highCount,
            int mediumCount,
            int lowCount,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            List<ViolationView> violations) {
    }

    public record Summary(Long attemptId, Integer trustScore, boolean flagged, int violationCount, boolean terminated,
                          boolean identityVerified) {
    }
}
