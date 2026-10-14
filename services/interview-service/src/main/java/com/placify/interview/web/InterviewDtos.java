package com.placify.interview.web;

import com.placify.interview.client.InterviewClients.CompanyStats;
import com.placify.interview.client.InterviewClients.ExperienceSummary;
import com.placify.interview.domain.InterviewEvaluation;
import com.placify.interview.domain.InterviewExperience;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class InterviewDtos {

    private InterviewDtos() {
    }

    // ------------------------------------------------------------ evaluations

    public record EvaluationRequest(
            @NotNull Long roundId,
            @NotBlank String studentUserId,
            @Size(max = 255) String interviewerName,
            @Min(1) @Max(10) int technical,
            @Min(1) @Max(10) int problemSolving,
            @Min(1) @Max(10) int communication,
            @Min(1) @Max(10) int cultureFit,
            @NotNull InterviewEvaluation.Result result,
            @Size(max = 3000) String strengths,
            @Size(max = 3000) String concerns,
            @Size(max = 3000) String feedback,
            boolean shareFeedback) {
    }

    public record EvaluationView(
            Long id,
            Long jobId,
            Long roundId,
            String studentUserId,
            String studentName,
            String regNo,
            String interviewerName,
            int technical,
            int problemSolving,
            int communication,
            int cultureFit,
            int overallScore,
            InterviewEvaluation.Result result,
            String strengths,
            String concerns,
            String feedback,
            boolean shareFeedback,
            LocalDateTime updatedAt) {

        public static EvaluationView of(InterviewEvaluation e, String name, String regNo) {
            return new EvaluationView(e.getId(), e.getJobId(), e.getRoundId(), e.getStudentUserId(), name, regNo,
                    e.getInterviewerName(), e.getTechnical(), e.getProblemSolving(), e.getCommunication(),
                    e.getCultureFit(), e.getOverallScore(), e.getResult(), e.getStrengths(), e.getConcerns(),
                    e.getFeedback(), e.isShareFeedback(), e.getUpdatedAt());
        }
    }

    /** A candidate waiting in an interview round, with their scorecard if already filled. */
    public record CandidateView(String studentUserId, String name, String regNo, String branch, BigDecimal cgpa,
                                List<String> skills, Integer bestAtsScore, EvaluationView evaluation) {
    }

    public record RoundBoard(Long roundId, String roundName, String roundType, String jobTitle,
                             List<CandidateView> pending, List<EvaluationView> evaluated) {
    }

    /** What a student sees about their own interview results. */
    public record MyInterviewResult(Long roundId, Long jobId, int overallScore, InterviewEvaluation.Result result,
                                    String feedback, LocalDateTime updatedAt) {
    }

    // ------------------------------------------------------------ experiences

    public record ExperienceRequest(
            @NotBlank @Size(max = 255) String companyName,
            @NotBlank @Size(max = 255) String jobRole,
            @Min(2000) @Max(2100) int year,
            @NotNull InterviewExperience.Outcome result,
            @NotNull InterviewExperience.Difficulty difficulty,
            @Size(max = 3000) String roundsSummary,
            @NotBlank @Size(min = 100, max = 20000) String content,
            @Size(max = 5000) String questionsAsked,
            @Size(max = 3000) String tips,
            boolean anonymous) {
    }

    public record ReviewRequest(boolean approve, @Size(max = 1000) String note) {
    }

    public record ExperienceView(
            Long id,
            String author,
            boolean mine,
            String companyName,
            String jobRole,
            int year,
            InterviewExperience.Outcome result,
            InterviewExperience.Difficulty difficulty,
            String roundsSummary,
            String content,
            String questionsAsked,
            String tips,
            InterviewExperience.Status status,
            String reviewNote,
            int upvotes,
            boolean votedByMe,
            LocalDateTime createdAt) {
    }

    public record CompanyCount(String companyName, long experiences) {
    }

    // ------------------------------------------------------------ prep hub

    public record PrepHub(
            String companyName,
            CompanyStats stats,
            ExperienceSummary aiSummary,
            LocalDateTime summaryGeneratedAt,
            long approvedExperiences,
            List<ExperienceView> topExperiences) {
    }
}
