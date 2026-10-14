package com.placify.analytics.web;

import com.placify.analytics.client.AnalyticsClients.AssessmentStats;
import com.placify.analytics.client.AnalyticsClients.AuthStats;
import com.placify.analytics.client.AnalyticsClients.CodingStats;
import com.placify.analytics.client.AnalyticsClients.MockStats;
import com.placify.analytics.client.AnalyticsClients.PlacementSummary;
import com.placify.analytics.client.AnalyticsClients.RoundFunnel;
import com.placify.analytics.domain.AuditLog;
import com.placify.analytics.ml.PlacementModel.ModelInfo;
import com.placify.analytics.service.ReadinessCalculator.Components;
import com.placify.analytics.service.ReadinessCalculator.Suggestion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Readiness(String userId, int score, String level, Components components, List<Suggestion> suggestions,
                            LocalDateTime computedAt) {
    }

    /** Minimal shape used by other services. */
    public record ReadinessScore(String userId, int score) {
    }

    public record Factor(String name, String impact, String detail) {
    }

    public record Prediction(String userId, double probability, int percent, String band, List<Factor> factors,
                             boolean modelTrained, String note) {
    }

    public record StudentDashboard(Readiness readiness, Prediction prediction, PlacementSummary placement,
                                   CodingStats coding, AssessmentStats aptitude, MockStats mockInterviews,
                                   Integer bestAtsScore, int profileCompleteness, Integer batchRank, Integer batchSize) {
    }

    // ------------------------------------------------------------- TPO

    public record CountPercent(String key, long total, long placed, double percent) {
    }

    public record CtcStats(BigDecimal average, BigDecimal median, BigDecimal highest, BigDecimal lowest) {
    }

    public record NamedCount(String name, long count) {
    }

    public record MonthCount(String month, long offers) {
    }

    public record TpoOverview(
            Integer batch,
            long students,
            long placed,
            double placementPercent,
            long eligibleUnplaced,
            long openJobs,
            long totalJobs,
            long applications,
            long offers,
            CtcStats ctc,
            List<CountPercent> byBranch,
            List<NamedCount> byTier,
            List<NamedCount> topRecruiters,
            List<MonthCount> offersByMonth,
            List<NamedCount> inDemandSkills,
            Double averageReadiness,
            long atRisk) {
    }

    public record AtRiskStudent(String userId, String regNo, String name, String branch, Integer batch, BigDecimal cgpa,
                                int readiness, int placementChance, String weakestArea, LocalDateTime computedAt) {
    }

    // ------------------------------------------------------------- company

    public record JobFunnel(Long jobId, String title, String status, long applicants, long selected, long offers,
                            List<RoundFunnel> rounds, List<NamedCount> applicantsByBranch) {
    }

    public record CompanyOverview(long jobs, long openJobs, long applicants, long selected, long offersMade,
                                  long offersAccepted, double acceptanceRate, List<JobFunnel> jobFunnels) {
    }

    // ------------------------------------------------------------- admin

    public record AdminOverview(AuthStats users, long auditEvents24h, long failedActions24h, List<NamedCount> topActions7d,
                                ModelInfo model, long readinessSnapshots, long placementRecords) {
    }

    public record AuditView(Long id, String service, String actorUserId, String actorRole, String action, String target,
                            String details, boolean success, LocalDateTime occurredAt) {

        public static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getService(), a.getActorUserId(), a.getActorRole(), a.getAction(),
                    a.getTarget(), a.getDetails(), a.isSuccess(), a.getOccurredAt());
        }
    }

    public record SeasonRecordResult(int batch, int recorded, int placed, ModelInfo model) {
    }

    public record Weights(Map<String, Double> weights) {
    }
}
