package com.placify.analytics.service;

import com.placify.analytics.AnalyticsProperties;
import com.placify.analytics.client.AnalyticsClients;
import com.placify.analytics.client.AnalyticsClients.ApplicationRow;
import com.placify.analytics.client.AnalyticsClients.JobRow;
import com.placify.analytics.client.AnalyticsClients.OfferRow;
import com.placify.analytics.client.AnalyticsClients.PlacementData;
import com.placify.analytics.client.AnalyticsClients.StudentSummary;
import com.placify.analytics.domain.ReadinessSnapshot;
import com.placify.analytics.repository.AuditLogRepository;
import com.placify.analytics.repository.PlacementRecordRepository;
import com.placify.analytics.repository.ReadinessSnapshotRepository;
import com.placify.analytics.web.AnalyticsDtos.AdminOverview;
import com.placify.analytics.web.AnalyticsDtos.AtRiskStudent;
import com.placify.analytics.web.AnalyticsDtos.CompanyOverview;
import com.placify.analytics.web.AnalyticsDtos.CountPercent;
import com.placify.analytics.web.AnalyticsDtos.CtcStats;
import com.placify.analytics.web.AnalyticsDtos.JobFunnel;
import com.placify.analytics.web.AnalyticsDtos.MonthCount;
import com.placify.analytics.web.AnalyticsDtos.NamedCount;
import com.placify.analytics.web.AnalyticsDtos.StudentDashboard;
import com.placify.analytics.web.AnalyticsDtos.TpoOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds the dashboards of every role. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AnalyticsClients clients;
    private final ReadinessService readiness;
    private final PredictionService prediction;
    private final ReadinessSnapshotRepository snapshots;
    private final AuditLogRepository audit;
    private final PlacementRecordRepository records;
    private final AnalyticsProperties properties;

    // ------------------------------------------------------------------ student

    public StudentDashboard student(String userId) {
        StudentSummary student = clients.student(userId).orElse(null);
        ReadinessSnapshot snap = readiness.compute(userId, student);
        Integer rank = null;
        Integer size = null;
        if (snap.getBatch() != null) {
            List<ReadinessSnapshot> batch = snapshots.findAll().stream()
                    .filter(s -> snap.getBatch().equals(s.getBatch())).toList();
            size = batch.size();
            rank = (int) batch.stream().filter(s -> s.getScore() > snap.getScore()).count() + 1;
        }
        return new StudentDashboard(readiness.explain(snap), prediction.predict(userId),
                clients.placement(userId).orElse(null), clients.coding(userId).orElse(null),
                clients.assessment(userId).orElse(null), clients.mock(userId).orElse(null),
                student == null ? null : student.bestAtsScore(), student == null ? 0 : student.completeness(), rank, size);
    }

    // ------------------------------------------------------------------ TPO

    public TpoOverview tpo(Integer batch) {
        List<StudentSummary> students = clients.students(null, batch);
        Set<String> studentIds = students.stream().map(StudentSummary::userId).collect(Collectors.toSet());
        PlacementData data = clients.placementData(null);

        List<OfferRow> offers = data.offers().stream()
                .filter(OfferRow::counts)
                .filter(o -> batch == null || studentIds.contains(o.studentUserId()))
                .toList();
        Set<String> placedIds = offers.stream().map(OfferRow::studentUserId).collect(Collectors.toSet());
        long placed = students.stream().filter(s -> placedIds.contains(s.userId())).count();

        Map<String, List<StudentSummary>> byBranch = students.stream()
                .collect(Collectors.groupingBy(s -> s.branch() == null ? "Unknown" : s.branch(), TreeMap::new, Collectors.toList()));
        List<CountPercent> branches = byBranch.entrySet().stream().map(e -> {
            long p = e.getValue().stream().filter(s -> placedIds.contains(s.userId())).count();
            return new CountPercent(e.getKey(), e.getValue().size(), p, pct(p, e.getValue().size()));
        }).toList();

        List<JobRow> jobs = data.jobs().stream().filter(j -> batch == null || j.batch() == null || batch.equals(j.batch())).toList();
        Set<Long> jobIds = jobs.stream().map(JobRow::id).collect(Collectors.toSet());
        List<ApplicationRow> apps = data.applications().stream().filter(a -> jobIds.contains(a.jobId())).toList();

        Map<String, Long> tiers = offers.stream().collect(Collectors.groupingBy(o -> o.tier() == null ? "NORMAL" : o.tier(), Collectors.counting()));
        Map<String, Long> recruiters = offers.stream().collect(Collectors.groupingBy(OfferRow::companyName, Collectors.counting()));
        Map<String, Long> months = offers.stream().filter(o -> o.verifiedAt() != null)
                .collect(Collectors.groupingBy(o -> o.verifiedAt().format(MONTH), TreeMap::new, Collectors.counting()));
        Map<String, Long> skills = jobs.stream().flatMap(j -> j.skills() == null ? java.util.stream.Stream.<String>empty() : j.skills().stream())
                .collect(Collectors.groupingBy(s -> s.trim().toLowerCase(Locale.ROOT), Collectors.counting()));

        List<ReadinessSnapshot> snaps = snapshots.findByUserIdIn(studentIds);
        Double avgReadiness = snaps.isEmpty() ? null
                : Math.round(snaps.stream().mapToInt(ReadinessSnapshot::getScore).average().orElse(0) * 10) / 10.0;
        long atRisk = snaps.stream().filter(s -> !placedIds.contains(s.getUserId()) && s.getScore() < properties.getAtRiskThreshold()).count();
        long eligibleUnplaced = students.stream().filter(s -> !placedIds.contains(s.userId()) && s.backlogs() == 0).count();

        return new TpoOverview(batch, students.size(), placed, pct(placed, students.size()), eligibleUnplaced,
                jobs.stream().filter(j -> "OPEN".equals(j.status())).count(), jobs.size(), apps.size(), offers.size(),
                ctc(offers.stream().map(OfferRow::ctcLpa).filter(Objects::nonNull).toList()), branches,
                top(tiers, 10), top(recruiters, 10),
                months.entrySet().stream().map(e -> new MonthCount(e.getKey(), e.getValue())).toList(),
                top(skills, 15), avgReadiness, atRisk);
    }

    /** Unplaced students with low readiness, worst first (uses cached scores refreshed nightly). */
    public List<AtRiskStudent> atRisk(Integer batch, Integer threshold) {
        int limit = threshold == null ? properties.getAtRiskThreshold() : threshold;
        List<StudentSummary> students = clients.students(null, batch);
        Map<String, StudentSummary> byId = students.stream()
                .collect(Collectors.toMap(StudentSummary::userId, Function.identity(), (a, b) -> a));
        Set<String> placedIds = clients.placementData(null).offers().stream().filter(OfferRow::counts)
                .map(OfferRow::studentUserId).collect(Collectors.toSet());
        Map<String, ReadinessSnapshot> snaps = snapshots.findByUserIdIn(byId.keySet()).stream()
                .collect(Collectors.toMap(ReadinessSnapshot::getUserId, Function.identity()));

        List<AtRiskStudent> out = new ArrayList<>();
        for (StudentSummary st : students) {
            if (placedIds.contains(st.userId())) {
                continue;
            }
            ReadinessSnapshot s = snaps.get(st.userId());
            if (s == null) {
                continue; // computed by the nightly job
            }
            if (s.getScore() < limit) {
                out.add(new AtRiskStudent(st.userId(), st.regNo(), st.name(), st.branch(), st.batch(), st.cgpa(),
                        s.getScore(), prediction.percentFor(s), ReadinessService.weakestArea(s), s.getComputedAt()));
            }
        }
        out.sort(Comparator.comparingInt(AtRiskStudent::readiness));
        return out;
    }

    // ------------------------------------------------------------------ company

    public CompanyOverview company(String companyUserId) {
        PlacementData data = clients.placementData(companyUserId);
        Map<Long, List<ApplicationRow>> appsByJob = data.applications().stream().collect(Collectors.groupingBy(ApplicationRow::jobId));
        Map<Long, List<OfferRow>> offersByJob = data.offers().stream().filter(o -> o.jobId() != null)
                .collect(Collectors.groupingBy(OfferRow::jobId));
        List<JobFunnel> funnels = data.jobs().stream().map(j -> {
            List<ApplicationRow> apps = appsByJob.getOrDefault(j.id(), List.of());
            Map<String, Long> branch = apps.stream().collect(Collectors.groupingBy(a -> a.branch() == null ? "Unknown" : a.branch(), Collectors.counting()));
            return new JobFunnel(j.id(), j.title(), j.status(), apps.size(),
                    apps.stream().filter(a -> "SELECTED".equals(a.status())).count(),
                    offersByJob.getOrDefault(j.id(), List.of()).size(),
                    data.funnels().stream().filter(f -> j.id().equals(f.jobId()))
                            .sorted(Comparator.comparingInt(f -> f.sequence())).toList(),
                    top(branch, 20));
        }).toList();
        long offersMade = data.offers().size();
        long accepted = data.offers().stream().filter(OfferRow::counts).count();
        return new CompanyOverview(data.jobs().size(), data.jobs().stream().filter(j -> "OPEN".equals(j.status())).count(),
                data.applications().size(), data.applications().stream().filter(a -> "SELECTED".equals(a.status())).count(),
                offersMade, accepted, pct(accepted, offersMade), funnels);
    }

    // ------------------------------------------------------------------ admin

    public AdminOverview admin() {
        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        List<NamedCount> actions = audit.actionCounts(LocalDateTime.now().minusDays(7)).stream().limit(10)
                .map(r -> new NamedCount((String) r[0], ((Number) r[1]).longValue())).toList();
        return new AdminOverview(clients.authStats().orElse(null), audit.countByOccurredAtAfter(dayAgo),
                audit.countBySuccessFalseAndOccurredAtAfter(dayAgo), actions, prediction.info(), snapshots.count(),
                records.count());
    }

    // ------------------------------------------------------------------ helpers

    static CtcStats ctc(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return new CtcStats(null, null, null, null);
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(sorted.size()), 2, RoundingMode.HALF_UP);
        int n = sorted.size();
        BigDecimal median = n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new CtcStats(avg, median, sorted.get(n - 1), sorted.get(0));
    }

    static List<NamedCount> top(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.<String, Long>comparingByKey()))
                .limit(limit).map(e -> new NamedCount(e.getKey(), e.getValue())).toList();
    }

    static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    /** Student id -> placed company name, for reports. */
    public Map<String, OfferRow> bestOffers(PlacementData data) {
        Map<String, OfferRow> best = new HashMap<>();
        for (OfferRow o : data.offers()) {
            if (!o.counts()) {
                continue;
            }
            best.merge(o.studentUserId(), o, (a, b) -> {
                BigDecimal ca = a.ctcLpa() == null ? BigDecimal.ZERO : a.ctcLpa();
                BigDecimal cb = b.ctcLpa() == null ? BigDecimal.ZERO : b.ctcLpa();
                return ca.compareTo(cb) >= 0 ? a : b;
            });
        }
        return best;
    }
}
