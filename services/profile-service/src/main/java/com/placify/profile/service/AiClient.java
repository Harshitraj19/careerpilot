package com.placify.profile.service;

import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Calls the AI helper service. Protected by a circuit breaker so profile
 * features keep working (without AI extras) when AI is unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final InternalClient client;

    public record SimilarityResponse(double similarity, String method) {
    }

    public record ParsedResume(
            List<String> skills,
            List<String> certifications,
            List<ParsedProject> projects,
            String internships,
            List<String> achievements,
            String summary,
            String githubUrl,
            String linkedinUrl) {
    }

    public record ParsedProject(String title, String description, String techStack, String link) {
    }

    public record ResumeFeedback(Integer fitScore, List<String> strengths, List<String> weaknesses, List<String> suggestions) {
    }

    /** Semantic similarity (0..1) using sentence embeddings, or -1 when unavailable. */
    @CircuitBreaker(name = "ai", fallbackMethod = "similarityFallback")
    public double similarity(String a, String b) {
        SimilarityResponse r = client.post(ServiceNames.AI_HELPER, "/internal/ai/similarity",
                Map.of("textA", a, "textB", b), SimilarityResponse.class);
        if (r == null) {
            return -1;
        }
        // word-overlap similarity (used when the embedding model is off) runs lower than embedding
        // similarity for related texts; map it onto the same scale so the ATS weights stay meaningful
        return "term-frequency".equals(r.method()) ? Math.min(1.0, 0.2 + r.similarity() * 1.6) : r.similarity();
    }

    double similarityFallback(String a, String b, Throwable t) {
        log.warn("Similarity unavailable: {}", t.getMessage());
        return -1;
    }

    @CircuitBreaker(name = "ai")
    public ParsedResume parseResume(String resumeText) {
        return client.post(ServiceNames.AI_HELPER, "/internal/ai/resume/parse",
                Map.of("resumeText", resumeText), ParsedResume.class);
    }

    @CircuitBreaker(name = "ai", fallbackMethod = "feedbackFallback")
    public ResumeFeedback feedback(String resumeText, String jobDescription) {
        return client.post(ServiceNames.AI_HELPER, "/internal/ai/resume/feedback",
                Map.of("resumeText", resumeText, "jobDescription", jobDescription), ResumeFeedback.class);
    }

    ResumeFeedback feedbackFallback(String resumeText, String jobDescription, Throwable t) {
        log.warn("AI resume feedback unavailable: {}", t.getMessage());
        return null;
    }
}
