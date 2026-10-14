package com.placify.submission.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Calls to coding-service (test cases, question details), assessment-service
 * (attempt checks) and ai-helper (code review).
 */
@Component
@RequiredArgsConstructor
public class SubmissionClients {

    private final InternalClient client;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestcaseDto(String id, String questionId, String input, String expectedOutput, boolean hidden) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestionDetail(String id, String title, String description, String inputFormat, String outputFormat,
                                 String constraints, String difficulty, String createdBy, String visibility, boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttemptCheck(boolean allowed, String reason, Long testId, String testOwnerUserId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeReview(String summary, String timeComplexity, String spaceComplexity, List<String> strengths,
                             List<String> improvements, List<String> edgeCases, Integer qualityScore) {
    }

    public List<TestcaseDto> testcases(String questionId) {
        return client.get(ServiceNames.CODING, "/internal/coding/questions/" + questionId + "/testcases",
                new ParameterizedTypeReference<List<TestcaseDto>>() {
                });
    }

    public QuestionDetail question(String questionId) {
        return client.get(ServiceNames.CODING, "/internal/coding/questions/" + questionId, QuestionDetail.class);
    }

    public boolean contestAllows(Long contestId, String userId, String questionId) {
        Boolean ok = client.get(ServiceNames.CODING, "/internal/coding/contests/" + contestId + "/can-submit?userId="
                + userId + "&questionId=" + questionId, Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    public AttemptCheck attemptCheck(Long attemptId, String userId, String questionId) {
        return client.get(ServiceNames.ASSESSMENT, "/internal/assessment/attempts/" + attemptId + "/can-submit?userId="
                + userId + "&questionId=" + questionId, AttemptCheck.class);
    }

    public String testOwner(Long testId) {
        Map<String, String> m = client.get(ServiceNames.ASSESSMENT, "/internal/assessment/tests/" + testId + "/owner",
                new ParameterizedTypeReference<Map<String, String>>() {
                });
        return m == null ? null : m.get("ownerUserId");
    }

    @CircuitBreaker(name = "ai")
    public CodeReview review(QuestionDetail q, String code, String language) {
        return client.post(ServiceNames.AI_HELPER, "/internal/ai/code-review",
                Map.of("title", q.title(), "description", q.description() == null ? "" : q.description(),
                        "code", code, "language", language),
                CodeReview.class);
    }
}
