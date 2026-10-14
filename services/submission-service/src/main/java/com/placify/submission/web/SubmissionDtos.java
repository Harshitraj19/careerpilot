package com.placify.submission.web;

import com.placify.submission.domain.PlagiarismCheck;
import com.placify.submission.domain.Submission;
import com.placify.submission.domain.SubmissionResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    public record SubmitRequest(
            @NotBlank String questionId,
            @NotNull Integer languageId,
            @NotBlank @Size(max = 65536, message = "Code is too long (max 64 KB)") String sourceCode,
            Submission.Mode mode,
            Long contestId,
            Long attemptId) {
    }

    public record RunRequest(
            @NotNull Integer languageId,
            @NotBlank @Size(max = 65536) String sourceCode,
            @Size(max = 10000) String stdin) {
    }

    public record RunResponse(String status, Integer statusId, String stdout, String stderr, String compileOutput,
                              String message, String time, String memory) {
    }

    public record SubmissionView(
            String id,
            String questionId,
            String userId,
            Integer languageId,
            String language,
            Submission.Status status,
            Submission.Mode mode,
            int testcasesPassed,
            int testcasesFailed,
            int totalTestcases,
            BigDecimal executionTime,
            BigDecimal memoryUsed,
            int score,
            String compileOutput,
            LocalDateTime submissionTime,
            String sourceCode) {
    }

    /** For hidden test cases only the verdict is shown. */
    public record ResultView(Long id, String testcaseId, boolean hidden, SubmissionResult.Verdict status,
                             BigDecimal executionTime, BigDecimal memoryUsed, String output, String errorMessage) {
    }

    public record LanguageView(int id, String name, String code) {
    }

    public record PlagiarismRequest(
            @NotNull PlagiarismCheck.Scope scope,
            @NotNull Long scopeId,
            @DecimalMin("0.3") @DecimalMax("1.0") BigDecimal threshold) {
    }

    public record PlagiarismCheckView(Long id, PlagiarismCheck.Scope scope, Long scopeId, BigDecimal threshold,
                                      int submissionsCompared, int pairsFlagged, String engine, LocalDateTime createdAt,
                                      List<PairView> pairs) {
    }

    public record PairView(Long id, String questionId, String submissionA, String submissionB, String userA, String userB,
                           BigDecimal similarity) {
    }

    public record PairDetail(PairView pair, SubmissionView a, SubmissionView b) {
    }
}
