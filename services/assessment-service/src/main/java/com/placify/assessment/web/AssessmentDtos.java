package com.placify.assessment.web;

import com.placify.assessment.domain.Attempt;
import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.domain.Test;
import com.placify.assessment.domain.TestPoolItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    // ---------------------------------------------------------------- MCQ bank

    public record McqRequest(
            @NotNull McqQuestion.Topic topic,
            @Size(max = 100) String subtopic,
            @NotNull McqQuestion.Difficulty difficulty,
            @NotBlank @Size(max = 5000) String text,
            @NotNull @Size(min = 2, max = 6) List<@NotBlank @Size(max = 1000) String> options,
            @Min(0) @Max(5) int correctIndex,
            @Size(max = 5000) String explanation,
            Boolean approved) {
    }

    public record McqView(Long id, McqQuestion.Topic topic, String subtopic, McqQuestion.Difficulty difficulty, String text,
                          List<String> options, Integer correctIndex, String explanation, McqQuestion.Source source,
                          boolean approved, LocalDateTime createdAt) {
    }

    public record GenerateRequest(
            @NotNull McqQuestion.Topic topic,
            @Size(max = 100) String subtopic,
            @NotNull McqQuestion.Difficulty difficulty,
            @Min(1) @Max(10) int count) {
    }

    // ---------------------------------------------------------------- practice

    /** Practice question without the answer. */
    public record PracticeQuestion(Long id, McqQuestion.Topic topic, String subtopic, McqQuestion.Difficulty difficulty,
                                   String text, List<String> options) {
    }

    public record PracticeAnswer(@NotNull Long questionId, @Min(0) int selectedIndex) {
    }

    public record PracticeFeedback(boolean correct, int correctIndex, String explanation) {
    }

    public record TopicStat(McqQuestion.Topic topic, int attempted, int correct, double accuracy) {
    }

    // ---------------------------------------------------------------- tests

    public record PoolCoding(@NotBlank String questionId, @Min(1) @Max(1000) int points) {
    }

    public record TestRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 2000) String instructions,
            @NotNull Test.Type type,
            Long roundId,
            @Min(5) @Max(600) int durationMinutes,
            @NotNull LocalDateTime windowStart,
            @NotNull LocalDateTime windowEnd,
            @DecimalMin("0") @DecimalMax("100") BigDecimal cutoffPercent,
            List<Long> mcqIds,
            @Min(0) @Max(200) int mcqCount,
            @Min(1) @Max(10) Integer marksPerMcq,
            List<@Valid PoolCoding> codingQuestions,
            @Min(0) @Max(10) int codingCount,
            boolean randomize,
            boolean proctored,
            boolean cameraRequired,
            @Min(1) @Max(50) Integer maxViolations,
            boolean autoSubmitOnViolations,
            List<String> proctoringChecks) {
    }

    public record TestView(
            Long id,
            String title,
            String instructions,
            Test.Type type,
            Test.Status status,
            String ownerUserId,
            String ownerName,
            Long jobId,
            Long roundId,
            int durationMinutes,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            BigDecimal cutoffPercent,
            int mcqCount,
            int marksPerMcq,
            int codingCount,
            boolean randomize,
            boolean proctored,
            boolean cameraRequired,
            int maxViolations,
            boolean autoSubmitOnViolations,
            List<String> proctoringChecks,
            Integer mcqPoolSize,
            Integer codingPoolSize,
            Long attempts,
            // for students
            Attempt.Status myAttemptStatus,
            Long myAttemptId) {
    }

    public record PoolItemView(Long id, TestPoolItem.Kind kind, String refId, int points, String title) {
    }

    // ---------------------------------------------------------------- attempts

    public record AttemptQuestionView(
            Long attemptQuestionId,
            TestPoolItem.Kind kind,
            int order,
            // MCQ
            String text,
            List<String> options,
            Integer selectedIndex,
            // coding
            String codingQuestionId,
            String codingTitle,
            String codingDifficulty,
            Integer codingPassed,
            Integer codingTotal,
            BigDecimal maxMarks) {
    }

    public record ProctoringSettings(boolean proctored, boolean cameraRequired, int maxViolations,
                                     boolean autoSubmitOnViolations, List<String> checks) {
    }

    public record AttemptView(
            Long attemptId,
            Long testId,
            String testTitle,
            String instructions,
            Attempt.Status status,
            LocalDateTime startedAt,
            LocalDateTime deadlineAt,
            long remainingSeconds,
            ProctoringSettings proctoring,
            List<AttemptQuestionView> questions) {
    }

    public record AnswerRequest(@NotNull Long attemptQuestionId, @NotNull @Min(0) Integer selectedIndex) {
    }

    public record AttemptResult(
            Long attemptId,
            Long testId,
            String testTitle,
            String studentUserId,
            String studentName,
            String studentLogin,
            Attempt.Status status,
            BigDecimal mcqScore,
            BigDecimal codingScore,
            BigDecimal totalScore,
            BigDecimal maxScore,
            BigDecimal percent,
            Boolean passed,
            Integer trustScore,
            boolean proctoringFlagged,
            String terminationReason,
            LocalDateTime startedAt,
            LocalDateTime submittedAt,
            Map<String, Object> breakdown) {
    }

    public record CodingStatement(String id, String title, String description, String inputFormat, String outputFormat,
                                  String constraints, String sampleInput, String sampleOutput, String timeLimit,
                                  String memoryLimit, List<Map<String, Object>> publicTestcases) {
    }
}
