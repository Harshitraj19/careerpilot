package com.placify.coding.web;

import com.placify.coding.domain.Contest;
import com.placify.coding.domain.Question;
import com.placify.coding.domain.Testcase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CodingDtos {

    private CodingDtos() {
    }

    public record QuestionRequest(
            @NotBlank @Size(max = 500) String title,
            @NotBlank String description,
            String inputFormat,
            String outputFormat,
            String constraints,
            String sampleInput,
            String sampleOutput,
            String explanation,
            @NotNull Question.Difficulty difficulty,
            List<@Size(max = 40) String> tags,
            Question.Visibility visibility,
            @DecimalMin("0.5") @DecimalMax("20") BigDecimal timeLimit,
            @DecimalMin("16") @DecimalMax("1024") BigDecimal memoryLimit,
            List<@Valid TestcaseRequest> testcases) {
    }

    public record TestcaseRequest(
            @NotNull String input,
            @NotNull String expectedOutput,
            Boolean hidden,
            BigDecimal timeLimit,
            BigDecimal memoryLimit) {
    }

    /** Full problem statement (never includes hidden test cases). */
    public record QuestionView(
            String id,
            String title,
            String description,
            String inputFormat,
            String outputFormat,
            String constraints,
            String sampleInput,
            String sampleOutput,
            String explanation,
            Question.Difficulty difficulty,
            List<String> tags,
            int rating,
            Question.Visibility visibility,
            BigDecimal timeLimit,
            BigDecimal memoryLimit,
            int solvedCount,
            int attemptCount,
            Boolean solvedByMe,
            boolean canEdit) {
    }

    public record QuestionSummary(String id, String title, Question.Difficulty difficulty, List<String> tags, int rating,
                                  Question.Visibility visibility, int solvedCount, int attemptCount, Boolean solvedByMe) {
    }

    /** For students the expected output of hidden cases is null. */
    public record TestcaseView(String id, String questionId, String input, String expectedOutput, boolean hidden,
                               BigDecimal timeLimit, BigDecimal memoryLimit) {

        public static TestcaseView of(Testcase t, boolean mask) {
            return new TestcaseView(t.getId(), t.getQuestionId(), mask && t.isHidden() ? null : t.getInput(),
                    mask && t.isHidden() ? null : t.getExpectedOutput(), t.isHidden(), t.getTimeLimit(), t.getMemoryLimit());
        }
    }

    public record PracticeStats(
            int rating,
            int solved,
            int submissions,
            int easySolved,
            int mediumSolved,
            int hardSolved,
            int contestsJoined,
            Map<String, Integer> solvedByTag,
            List<String> weakTags,
            List<QuestionSummary> recentlySolved,
            Integer rank) {
    }

    public record NextProblem(QuestionSummary question, String reason, int yourRating) {
    }

    public record ContestRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @NotEmpty List<@Valid ContestQuestionRequest> questions) {
    }

    public record ContestQuestionRequest(@NotBlank String questionId, @Min(1) @Max(1000) int points) {
    }

    public record ContestView(
            Long id,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Contest.Phase phase,
            long participants,
            boolean joined,
            List<ContestProblem> problems) {
    }

    public record ContestProblem(String questionId, String title, Question.Difficulty difficulty, int points,
                                 Boolean solvedByMe, Integer wrongAttempts) {
    }

    public record BadgeView(String code, String title, String description, LocalDateTime awardedAt) {
    }

    public record HallOfFameEntry(int rank, String userId, String name, int rating, int solved, List<String> badges) {
    }

    public record IdsRequest(List<String> ids) {
    }

    public record UserCodingStats(String userId, int rating, int solved, int submissions, int easySolved,
                                  int mediumSolved, int hardSolved, int contestsJoined, Double averageContestPercentile) {
    }
}
