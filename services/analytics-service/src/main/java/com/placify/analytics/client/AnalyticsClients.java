package com.placify.analytics.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Read-only calls to every service that owns data the dashboards need. */
@Component
@RequiredArgsConstructor
public class AnalyticsClients {

    private final InternalClient client;

    // ------------------------------------------------------------- records

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudentSummary(String userId, String regNo, String name, String email, String branch, Integer batch,
                                 BigDecimal cgpa, int backlogs, List<String> skills, int completeness,
                                 boolean hasResume, Integer latestAtsScore, Integer bestAtsScore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodingStats(String userId, int rating, int solved, int submissions, int easySolved, int mediumSolved,
                              int hardSolved, int contestsJoined, Double averageContestPercentile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopicStat(String topic, int attempted, int correct, double accuracy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssessmentStats(String userId, List<TopicStat> practice, double practiceAccuracy, int testsTaken,
                                  Double averageTestPercent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MockStats(String userId, int completed, Double averageScore, Integer bestScore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlacementSummary(String studentUserId, long applications, long active, long selected, long rejected,
                                   long offers, boolean placed, String placedTier, BigDecimal bestCtc,
                                   long roundsCleared, long roundsTaken, Double averageRoundScore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobRow(Long id, String companyUserId, String companyName, String title, BigDecimal ctcLpa, String tier,
                         String status, List<String> skills, Integer batch, LocalDateTime publishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApplicationRow(Long id, Long jobId, String studentUserId, String branch, String status,
                                 LocalDateTime appliedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfferRow(Long id, Long jobId, String studentUserId, String companyName, BigDecimal ctcLpa, String tier,
                           String status, boolean offCampus, LocalDateTime verifiedAt) {

        public boolean counts() {
            return "VERIFIED".equals(status) || "ACCEPTED".equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoundFunnel(Long jobId, Long roundId, int sequence, String name, String type, long total, long cleared,
                              long rejected, long pending, long underReview, Double averageScore) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlacementData(List<JobRow> jobs, List<ApplicationRow> applications, List<OfferRow> offers,
                                List<RoundFunnel> funnels) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthStats(long students, long companies, long pendingCompanies, long tpos, long admins,
                            long blockedUsers, long successfulLogins7d, long failedLogins7d) {
    }

    // ------------------------------------------------------------- calls

    public Optional<StudentSummary> student(String userId) {
        return client.tryGet(ServiceNames.PROFILE, "/internal/profile/students/" + userId, StudentSummary.class);
    }

    public List<StudentSummary> students(String branch, Integer batch) {
        StringBuilder path = new StringBuilder("/internal/profile/students?");
        if (branch != null && !branch.isBlank()) {
            path.append("branch=").append(InternalClient.q(branch)).append('&');
        }
        if (batch != null) {
            path.append("batch=").append(batch);
        }
        return client.get(ServiceNames.PROFILE, path.toString(), new ParameterizedTypeReference<List<StudentSummary>>() {
        });
    }

    public Optional<CodingStats> coding(String userId) {
        return client.tryGet(ServiceNames.CODING, "/internal/coding/users/" + userId + "/stats", CodingStats.class);
    }

    public Optional<AssessmentStats> assessment(String userId) {
        return client.tryGet(ServiceNames.ASSESSMENT, "/internal/assessment/users/" + userId + "/stats", AssessmentStats.class);
    }

    public Optional<MockStats> mock(String userId) {
        return client.tryGet(ServiceNames.AI_HELPER, "/internal/ai/users/" + userId + "/mock-stats", MockStats.class);
    }

    public Optional<PlacementSummary> placement(String userId) {
        return client.tryGet(ServiceNames.PLACEMENT, "/internal/placement/students/" + userId + "/summary",
                PlacementSummary.class);
    }

    public PlacementData placementData(String companyUserId) {
        String path = "/internal/placement/analytics" + (companyUserId == null ? "" : "?companyUserId=" + InternalClient.q(companyUserId));
        return client.get(ServiceNames.PLACEMENT, path, PlacementData.class);
    }

    public Optional<AuthStats> authStats() {
        return client.tryGet(ServiceNames.AUTH, "/internal/auth/stats", AuthStats.class);
    }
}
