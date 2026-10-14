package com.placify.assessment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls to placement (rounds and candidates), coding (problem statements),
 * proctoring (trust score) and the AI helper (MCQ generation).
 */
@Component
@RequiredArgsConstructor
public class AssessmentClients {

    private final InternalClient client;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoundInfo(Long roundId, Long jobId, String jobTitle, String companyUserId, String companyName,
                            int sequence, String name, String type, BigDecimal cutoffPercent, Long assessmentTestId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodingQuestion(String id, String title, String description, String inputFormat, String outputFormat,
                                 String constraints, String sampleInput, String sampleOutput, String difficulty,
                                 List<String> tags, String timeLimit, String memoryLimit, String createdBy,
                                 String visibility, boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Testcase(String id, String input, String expectedOutput, boolean hidden) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProctoringSummary(Long attemptId, Integer trustScore, boolean flagged, int violationCount,
                                    boolean terminated, boolean identityVerified) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedMcq(String question, List<String> options, Integer correctIndex, String explanation) {
    }

    /** First-round online tests of open drives without a linked test, with their schedule. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenTestRound(Long roundId, Long jobId, String jobTitle, String companyUserId, String companyName,
                                String name, String type, BigDecimal cutoffPercent, LocalDateTime scheduledAt,
                                String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodingSummary(String id, String title, String difficulty, String visibility) {
    }

    public List<OpenTestRound> openTestRounds() {
        return client.get(ServiceNames.PLACEMENT, "/internal/placement/open-test-rounds",
                new ParameterizedTypeReference<List<OpenTestRound>>() {
                });
    }

    public List<CodingSummary> codingQuestionsBy(String authorUserId) {
        return client.get(ServiceNames.CODING, "/internal/coding/questions/by-author/" + authorUserId,
                new ParameterizedTypeReference<List<CodingSummary>>() {
                });
    }

    public RoundInfo round(Long roundId) {
        return client.get(ServiceNames.PLACEMENT, "/internal/placement/rounds/" + roundId, RoundInfo.class);
    }

    public void linkRound(Long roundId, Long testId) {
        client.post(ServiceNames.PLACEMENT, "/internal/placement/rounds/" + roundId + "/test-link",
                Map.of("assessmentTestId", testId), Map.class);
    }

    public List<String> roundCandidates(Long roundId) {
        return client.get(ServiceNames.PLACEMENT, "/internal/placement/rounds/" + roundId + "/candidates",
                new ParameterizedTypeReference<List<String>>() {
                });
    }

    public boolean isRoundCandidate(Long roundId, String studentUserId) {
        Boolean ok = client.get(ServiceNames.PLACEMENT, "/internal/placement/rounds/" + roundId + "/candidates/" + studentUserId,
                Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    public CodingQuestion codingQuestion(String id) {
        return client.get(ServiceNames.CODING, "/internal/coding/questions/" + id, CodingQuestion.class);
    }

    public List<Testcase> codingTestcases(String id) {
        return client.get(ServiceNames.CODING, "/internal/coding/questions/" + id + "/testcases",
                new ParameterizedTypeReference<List<Testcase>>() {
                });
    }

    public Optional<ProctoringSummary> proctoring(Long attemptId) {
        return client.tryGet(ServiceNames.PROCTORING, "/internal/proctoring/attempts/" + attemptId + "/summary",
                ProctoringSummary.class);
    }

    @CircuitBreaker(name = "ai")
    public List<GeneratedMcq> generateMcqs(String topic, String subtopic, String difficulty, int count) {
        return client.post(ServiceNames.AI_HELPER, "/internal/ai/mcq/generate",
                Map.of("topic", topic, "subtopic", subtopic == null ? "" : subtopic, "difficulty", difficulty, "count", count),
                new ParameterizedTypeReference<List<GeneratedMcq>>() {
                });
    }
}
