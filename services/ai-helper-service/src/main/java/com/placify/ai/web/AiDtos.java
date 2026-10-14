package com.placify.ai.web;

import com.placify.ai.domain.MockInterview;
import com.placify.ai.domain.TutorMessage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    // ---------------------------------------------------------------- tutor

    public record TutorQuery(
            @NotBlank String questionId,
            @NotBlank @Size(max = 2000) String query,
            @Size(max = 30000) String code,
            @Size(max = 40) String language) {
    }

    /** Structured tutor answer (what the model must return). */
    public record TutorAnswer(String answer, Integer hintLevel, String nextStep) {
    }

    public record ChatItem(Long id, TutorMessage.Role role, String content, LocalDateTime time) {
    }

    public record TutorHistory(Long sessionId, int hintLevel, List<ChatItem> messages) {
    }

    // ---------------------------------------------------------------- mock interview

    public record StartInterview(
            @NotBlank @Size(max = 120) String role,
            @NotNull MockInterview.Type type,
            List<@Size(max = 40) String> skills,
            @Size(max = 10) String difficulty,
            @Min(3) @Max(10) int questionCount) {
    }

    public record AnswerRequest(@NotBlank @Size(max = 6000) String answer) {
    }

    /** What the model returns when it evaluates an answer. */
    public record AnswerEvaluation(Integer score, String feedback, List<String> idealPoints) {
    }

    /** What the model returns at the end of the interview. */
    public record InterviewSummary(Integer overallScore, List<String> strengths, List<String> improvements,
                                   String verdict, List<String> topicsToRevise) {
    }

    public record TurnView(int seq, String question, String answer, Integer score, String feedback, List<String> idealPoints) {
    }

    public record InterviewView(
            Long id,
            String role,
            MockInterview.Type type,
            String difficulty,
            int questionCount,
            MockInterview.Status status,
            Integer overallScore,
            InterviewSummary summary,
            String currentQuestion,
            int answered,
            List<TurnView> turns,
            LocalDateTime createdAt) {
    }

    public record MockStats(String userId, int completed, Double averageScore, Integer bestScore) {
    }

    // ---------------------------------------------------------------- tools

    public record InterviewQuestionsRequest(
            @NotBlank @Size(max = 120) String role,
            List<@Size(max = 40) String> skills,
            @Min(3) @Max(20) int count) {
    }

    public record InterviewQuestion(String question, String type, String difficulty, String hint) {
    }

    public record CodeReviewRequest(String title, String description, String code, String language) {
    }

    public record CodeReview(String summary, String timeComplexity, String spaceComplexity, List<String> strengths,
                             List<String> improvements, List<String> edgeCases, Integer qualityScore) {
    }

    public record ResumeParseRequest(String resumeText) {
    }

    public record ParsedProject(String title, String description, String techStack, String link) {
    }

    public record ParsedResume(List<String> skills, List<String> certifications, List<ParsedProject> projects,
                               String internships, List<String> achievements, String summary, String githubUrl,
                               String linkedinUrl) {
    }

    public record ResumeFeedbackRequest(String resumeText, String jobDescription) {
    }

    public record ResumeFeedback(Integer fitScore, List<String> strengths, List<String> weaknesses, List<String> suggestions) {
    }

    public record McqGenerateRequest(String topic, String subtopic, String difficulty, int count) {
    }

    public record GeneratedMcq(String question, List<String> options, Integer correctIndex, String explanation) {
    }

    public record SummarizeRequest(String company, List<String> experiences) {
    }

    public record ExperienceSummary(String summary, List<String> commonTopics, String roundPattern, String difficulty,
                                    List<String> tips) {
    }

    public record SimilarityRequest(String textA, String textB) {
    }
}
