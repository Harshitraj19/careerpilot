package com.placify.placement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls from placement-service to other services. Optional data (AI similarity,
 * readiness score, problem recommendations) never blocks the placement flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlacementClients {

    private final InternalClient client;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Similarity(double similarity, String method) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Readiness(String userId, int score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProblemSuggestion(String id, String title, String difficulty, List<String> tags) {
    }

    // ---------------------------------------------------------------- profile

    public StudentInfo student(String userId) {
        return client.get(ServiceNames.PROFILE, "/internal/profile/students/" + userId, StudentInfo.class);
    }

    public List<StudentInfo> students(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return client.post(ServiceNames.PROFILE, "/internal/profile/students/batch", Map.of("ids", ids),
                new ParameterizedTypeReference<List<StudentInfo>>() {
                });
    }

    public List<StudentInfo> allStudents(Integer batch) {
        String path = "/internal/profile/students" + (batch == null ? "" : "?batch=" + batch);
        return client.get(ServiceNames.PROFILE, path, new ParameterizedTypeReference<List<StudentInfo>>() {
        });
    }

    public String resumeText(String userId) {
        return client.tryGet(ServiceNames.PROFILE, "/internal/profile/students/" + userId + "/resume-text",
                        new ParameterizedTypeReference<Map<String, String>>() {
                        })
                .map(m -> m.getOrDefault("text", ""))
                .orElse("");
    }

    // ---------------------------------------------------------------- AI

    /** 0..1 semantic similarity, or -1 if the AI service is not available. */
    @CircuitBreaker(name = "ai", fallbackMethod = "similarityFallback")
    public double similarity(String a, String b) {
        Similarity s = client.post(ServiceNames.AI_HELPER, "/internal/ai/similarity", Map.of("textA", a, "textB", b), Similarity.class);
        return s == null ? -1 : s.similarity();
    }

    double similarityFallback(String a, String b, Throwable t) {
        return -1;
    }

    // ---------------------------------------------------------------- analytics

    public Optional<Integer> readiness(String userId) {
        return client.tryGet(ServiceNames.ANALYTICS, "/internal/analytics/readiness/" + userId, Readiness.class)
                .map(Readiness::score);
    }

    // ---------------------------------------------------------------- coding

    public List<ProblemSuggestion> recommendProblems(Collection<String> skills, int limit) {
        if (skills.isEmpty()) {
            return List.of();
        }
        String tags = String.join(",", skills);
        return client.tryGet(ServiceNames.CODING,
                        "/internal/coding/recommend?tags=" + InternalClient.q(tags) + "&limit=" + limit,
                        new ParameterizedTypeReference<List<ProblemSuggestion>>() {
                        })
                .orElse(List.of());
    }
}
