package com.placify.interview.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Calls to placement, profile and AI services. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewClients {

    private final InternalClient client;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoundInfo(Long roundId, Long jobId, String jobTitle, String companyUserId, String companyName,
                            int sequence, String name, String type, BigDecimal cutoffPercent, Long assessmentTestId,
                            LocalDateTime scheduledAt) {

        public boolean isInterviewLike() {
            return "TECHNICAL_INTERVIEW".equals(type) || "HR_INTERVIEW".equals(type)
                    || "GROUP_DISCUSSION".equals(type) || "OTHER".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudentSummary(String userId, String regNo, String name, String email, String branch, Integer batch,
                                 BigDecimal cgpa, List<String> skills, Integer bestAtsScore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YearStat(int year, long applications, long shortlisted, long selected, BigDecimal averageCtc) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyStats(String companyName, List<YearStat> years, List<String> roundPattern,
                               List<Long> testIds, List<String> skillsAsked) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperienceSummary(String summary, List<String> commonTopics, String roundPattern, String difficulty,
                                    List<String> tips) {
    }

    public RoundInfo round(Long roundId) {
        return client.get(ServiceNames.PLACEMENT, "/internal/placement/rounds/" + roundId, RoundInfo.class);
    }

    public List<String> candidates(Long roundId) {
        return client.get(ServiceNames.PLACEMENT, "/internal/placement/rounds/" + roundId + "/candidates",
                new ParameterizedTypeReference<List<String>>() {
                });
    }

    public boolean isCandidate(Long roundId, String studentUserId) {
        Boolean b = client.get(ServiceNames.PLACEMENT,
                "/internal/placement/rounds/" + roundId + "/candidates/" + studentUserId, Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    public List<StudentSummary> students(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return client.post(ServiceNames.PROFILE, "/internal/profile/students/batch", Map.of("ids", ids),
                new ParameterizedTypeReference<List<StudentSummary>>() {
                });
    }

    @CircuitBreaker(name = "placement", fallbackMethod = "statsFallback")
    public CompanyStats companyStats(String companyName) {
        return client.get(ServiceNames.PLACEMENT, "/internal/placement/company-stats?companyName="
                + InternalClient.q(companyName), CompanyStats.class);
    }

    CompanyStats statsFallback(String companyName, Throwable t) {
        log.warn("Company stats unavailable: {}", t.getMessage());
        return new CompanyStats(companyName, List.of(), List.of(), List.of(), List.of());
    }

    @CircuitBreaker(name = "ai")
    public ExperienceSummary summarize(String company, List<String> experiences) {
        return client.post(ServiceNames.AI_HELPER, "/internal/ai/summarize-experiences",
                Map.of("company", company, "experiences", experiences), ExperienceSummary.class);
    }
}
