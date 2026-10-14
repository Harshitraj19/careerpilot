package com.placify.common.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every event exchanged between services over RabbitMQ.
 * Kept in one place so producers and consumers always agree on the shape.
 */
public final class Events {

    private Events() {
    }

    /** Ask the notification service to notify users (in-app + optional email). */
    public record NotificationEvent(
            List<String> userIds,
            String title,
            String message,
            String type,
            String link,
            boolean sendEmail,
            List<String> extraEmails,
            String emailSubject) {

        public static NotificationEvent inApp(List<String> userIds, String title, String message, String type, String link) {
            return new NotificationEvent(userIds, title, message, type, link, false, List.of(), title);
        }

        public static NotificationEvent withEmail(List<String> userIds, String title, String message, String type, String link) {
            return new NotificationEvent(userIds, title, message, type, link, true, List.of(), title);
        }

        public static NotificationEvent emailOnly(String email, String subject, String message) {
            return new NotificationEvent(List.of(), subject, message, "EMAIL", null, true, List.of(email), subject);
        }
    }

    /** A student account was created by bulk import (auth -> profile). */
    public record StudentImportedEvent(
            String userId,
            String regNo,
            String name,
            String email,
            String branch,
            Integer batch,
            BigDecimal cgpa,
            String phone) {
    }

    /** The TPO approved a company account (auth -> profile). */
    public record CompanyApprovedEvent(
            String userId,
            String companyName,
            String email,
            String website,
            String industry) {
    }

    /** A test attempt has been graded (assessment -> placement, analytics). */
    public record AssessmentCompletedEvent(
            Long attemptId,
            Long testId,
            Long jobId,
            Long roundId,
            String studentUserId,
            double percent,
            boolean passed,
            Integer trustScore,
            boolean proctoringFlagged) {
    }

    /** A company recorded the result of an interview round (interview -> placement). */
    public record InterviewEvaluatedEvent(
            Long jobId,
            Long roundId,
            String studentUserId,
            String result,
            Integer score,
            String feedback) {
    }

    /** A code submission was fully judged (submission -> coding, assessment). */
    public record SubmissionJudgedEvent(
            String submissionId,
            String userId,
            String questionId,
            String mode,
            Long contestId,
            Long attemptId,
            String verdict,
            int passed,
            int total,
            Integer languageId) {
    }

    /** Proctoring decided the attempt must be ended (proctoring -> assessment). */
    public record ProctoringTerminateEvent(Long attemptId, Long sessionId, String reason) {
    }

    /** Live warning for the student in the exam (proctoring -> notification -> WebSocket). */
    public record ProctoringWarningEvent(
            String userId,
            Long sessionId,
            Long attemptId,
            String violationType,
            String message,
            int violationCount,
            int maxViolations) {
    }

    /** A camera frame waiting for analysis (proctoring -> proctoring worker). */
    public record FrameAnalysisRequest(Long sessionId, String snapshotPath, LocalDateTime capturedAt) {
    }

    /** One row of a contest leaderboard. */
    public record LeaderboardEntry(int rank, String userId, String name, int score, long penaltyMinutes) {
    }

    /** Contest leaderboard changed (coding -> notification -> WebSocket). */
    public record LeaderboardUpdatedEvent(Long contestId, List<LeaderboardEntry> entries) {
    }

    /** A student's offer was verified (placement -> analytics). */
    public record StudentPlacedEvent(
            String studentUserId,
            Long offerId,
            String companyName,
            BigDecimal ctcLpa,
            String tier) {
    }

    /** Audit trail entry (any service -> analytics). */
    public record AuditEvent(
            String service,
            String actorUserId,
            String actorRole,
            String action,
            String target,
            String details,
            boolean success,
            LocalDateTime timestamp) {
    }
}
