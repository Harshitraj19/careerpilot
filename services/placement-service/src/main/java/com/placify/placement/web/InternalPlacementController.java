package com.placify.placement.web;

import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Offer;
import com.placify.placement.domain.Round;
import com.placify.placement.domain.RoundResult;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.JobRepository;
import com.placify.placement.repository.OfferRepository;
import com.placify.placement.repository.RoundRepository;
import com.placify.placement.repository.RoundResultRepository;
import com.placify.placement.service.ApplicationService;
import com.placify.placement.service.EligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Service-to-service endpoints (assessment, interview, profile, analytics).
 */
@RestController
@RequestMapping("/internal/placement")
@RequiredArgsConstructor
public class InternalPlacementController {

    private final JobRepository jobs;
    private final RoundRepository rounds;
    private final ApplicationRepository applications;
    private final RoundResultRepository results;
    private final OfferRepository offers;
    private final EligibilityService eligibility;
    private final ApplicationService applicationService;

    public record RoundInfo(Long roundId, Long jobId, String jobTitle, String companyUserId, String companyName,
                            int sequence, String name, Round.Type type, BigDecimal cutoffPercent, Long assessmentTestId,
                            LocalDateTime scheduledAt, String description) {
    }

    public record LinkRequest(Long assessmentTestId) {
    }

    public record StudentPlacementSummary(String studentUserId, long applications, long active, long selected,
                                          long rejected, long offers, boolean placed, String placedTier,
                                          BigDecimal bestCtc, long roundsCleared, long roundsTaken,
                                          Double averageRoundScore) {
    }

    public record JobRow(Long id, String companyUserId, String companyName, String title, BigDecimal ctcLpa,
                         Job.Tier tier, Job.Status status, List<String> skills, Integer batch, LocalDateTime publishedAt) {
    }

    public record ApplicationRow(Long id, Long jobId, String studentUserId, String branch, Application.Status status,
                                 LocalDateTime appliedAt) {
    }

    public record OfferRow(Long id, Long jobId, String studentUserId, String companyName, BigDecimal ctcLpa,
                           Job.Tier tier, Offer.Status status, boolean offCampus, LocalDateTime verifiedAt) {
    }

    public record RoundFunnel(Long jobId, Long roundId, int sequence, String name, Round.Type type,
                              long total, long cleared, long rejected, long pending, long underReview,
                              Double averageScore) {
    }

    public record AnalyticsData(List<JobRow> jobs, List<ApplicationRow> applications, List<OfferRow> offers,
                                List<RoundFunnel> funnels) {
    }

    public record YearStat(int year, long applications, long shortlisted, long selected, BigDecimal averageCtc) {
    }

    public record CompanyStats(String companyName, List<YearStat> years, List<String> roundPattern,
                               List<Long> testIds, List<String> skillsAsked) {
    }

    @GetMapping("/access")
    public boolean companyCanSee(@RequestParam String companyUserId, @RequestParam String studentUserId) {
        return applications.countCompanyApplicant(companyUserId, studentUserId) > 0;
    }

    @GetMapping("/rounds/{roundId}")
    public RoundInfo round(@PathVariable Long roundId) {
        Round r = rounds.findById(roundId).orElseThrow(() -> ApiException.notFound("Round not found"));
        Job j = jobs.findById(r.getJobId()).orElseThrow(() -> ApiException.notFound("Job not found"));
        return new RoundInfo(r.getId(), j.getId(), j.getTitle(), j.getCompanyUserId(), j.getCompanyName(),
                r.getSequence(), r.getName(), r.getType(), r.getCutoffPercent(), r.getAssessmentTestId(), r.getScheduledAt(),
                r.getDescription());
    }

    /** Students currently waiting in this round (allowed to take its test). */
    @GetMapping("/rounds/{roundId}/candidates")
    public List<String> candidates(@PathVariable Long roundId) {
        return results.findByRoundIdAndStatusIn(roundId, EnumSet.of(RoundResult.Status.PENDING)).stream()
                .map(RoundResult::getStudentUserId).toList();
    }

    @GetMapping("/rounds/{roundId}/candidates/{studentUserId}")
    public boolean isCandidate(@PathVariable Long roundId, @PathVariable String studentUserId) {
        return results.findByRoundIdAndStudentUserId(roundId, studentUserId)
                .map(r -> r.getStatus() == RoundResult.Status.PENDING)
                .orElse(false);
    }

    @PostMapping("/rounds/{roundId}/test-link")
    @Transactional
    public RoundInfo link(@PathVariable Long roundId, @RequestBody LinkRequest req) {
        Round r = rounds.findById(roundId).orElseThrow(() -> ApiException.notFound("Round not found"));
        r.setAssessmentTestId(req.assessmentTestId());
        // students who applied before the test existed get it now (open online-test rounds)
        Job job = jobs.findById(r.getJobId()).orElseThrow(() -> ApiException.notFound("Job not found"));
        applications.findByJobIdAndStatus(job.getId(), Application.Status.APPLIED)
                .forEach(a -> applicationService.enterOpenTest(a, job));
        return round(roundId);
    }

    /** First-round online tests of open drives that have no assessment linked yet (used by the demo seeder). */
    @GetMapping("/open-test-rounds")
    public List<RoundInfo> openTestRounds() {
        List<RoundInfo> out = new ArrayList<>();
        for (Job j : jobs.findByStatusOrderByPublishedAtDesc(Job.Status.OPEN)) {
            rounds.findFirstByJobIdOrderBySequenceAsc(j.getId())
                    .filter(r -> r.getType().isTest() && r.getAssessmentTestId() == null)
                    .ifPresent(r -> out.add(round(r.getId())));
        }
        return out;
    }

    @GetMapping("/students/{studentUserId}/summary")
    public StudentPlacementSummary student(@PathVariable String studentUserId) {
        List<Application> apps = applications.findByStudentUserIdOrderByAppliedAtDesc(studentUserId);
        List<Offer> list = offers.findByStudentUserIdOrderByCreatedAtDesc(studentUserId);
        List<RoundResult> rr = results.findByStudentUserId(studentUserId);
        Job.Tier tier = eligibility.placedTier(studentUserId);
        BigDecimal best = list.stream()
                .filter(o -> o.getStatus() == Offer.Status.VERIFIED || o.getStatus() == Offer.Status.ACCEPTED)
                .map(Offer::getCtcLpa).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        long decided = rr.stream().filter(r -> r.getStatus() == RoundResult.Status.CLEARED
                || r.getStatus() == RoundResult.Status.REJECTED).count();
        Double avg = rr.stream().filter(r -> r.getScore() != null)
                .mapToDouble(r -> r.getScore().doubleValue()).average().stream().boxed().findFirst().orElse(null);
        return new StudentPlacementSummary(studentUserId, apps.size(),
                apps.stream().filter(a -> a.getStatus().isActive()).count(),
                apps.stream().filter(a -> a.getStatus() == Application.Status.SELECTED).count(),
                apps.stream().filter(a -> a.getStatus() == Application.Status.REJECTED).count(),
                list.size(), tier != null, tier == null ? null : tier.name(), best,
                rr.stream().filter(r -> r.getStatus() == RoundResult.Status.CLEARED).count(), decided, avg);
    }

    @GetMapping("/analytics")
    public AnalyticsData analytics(@RequestParam(required = false) String companyUserId) {
        List<Job> jobList = companyUserId == null ? jobs.findAll() : jobs.findByCompanyUserIdOrderByCreatedAtDesc(companyUserId);
        List<Long> jobIds = jobList.stream().map(Job::getId).toList();
        List<Application> apps = jobIds.isEmpty() ? List.of() : applications.findByJobIdIn(jobIds);
        List<Offer> offerList = offers.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> companyUserId == null || companyUserId.equals(o.getCompanyUserId()))
                .toList();
        return new AnalyticsData(
                jobList.stream().map(j -> new JobRow(j.getId(), j.getCompanyUserId(), j.getCompanyName(), j.getTitle(),
                        j.getCtcLpa(), j.getTier(), j.getStatus(), Texts.toList(j.getRequiredSkills()), j.getEligibleBatch(),
                        j.getPublishedAt())).toList(),
                apps.stream().map(a -> new ApplicationRow(a.getId(), a.getJobId(), a.getStudentUserId(), a.getBranch(),
                        a.getStatus(), a.getAppliedAt())).toList(),
                offerList.stream().map(o -> new OfferRow(o.getId(), o.getJobId(), o.getStudentUserId(), o.getCompanyName(),
                        o.getCtcLpa(), o.getTier(), o.getStatus(), o.isOffCampus(), o.getVerifiedAt())).toList(),
                funnels(jobIds));
    }

    private List<RoundFunnel> funnels(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        List<Round> roundList = rounds.findByJobIdIn(jobIds);
        Map<Long, List<RoundResult>> byRound = results.findByRoundIdIn(roundList.stream().map(Round::getId).toList())
                .stream().collect(Collectors.groupingBy(RoundResult::getRoundId));
        List<RoundFunnel> out = new ArrayList<>();
        for (Round r : roundList) {
            List<RoundResult> rr = byRound.getOrDefault(r.getId(), List.of());
            Double avg = rr.stream().filter(x -> x.getScore() != null).mapToDouble(x -> x.getScore().doubleValue())
                    .average().stream().boxed().findFirst().orElse(null);
            out.add(new RoundFunnel(r.getJobId(), r.getId(), r.getSequence(), r.getName(), r.getType(), rr.size(),
                    count(rr, RoundResult.Status.CLEARED), count(rr, RoundResult.Status.REJECTED) + count(rr, RoundResult.Status.ABSENT),
                    count(rr, RoundResult.Status.PENDING), count(rr, RoundResult.Status.UNDER_REVIEW), avg));
        }
        return out;
    }

    private static long count(List<RoundResult> list, RoundResult.Status status) {
        return list.stream().filter(r -> r.getStatus() == status).count();
    }

    /** Year-wise selection statistics and round pattern of a company (for the Company Prep Hub). */
    @GetMapping("/company-stats")
    public CompanyStats companyStats(@RequestParam String companyName) {
        List<Job> list = jobs.findByCompanyNameIgnoreCase(companyName.trim());
        if (list.isEmpty()) {
            return new CompanyStats(companyName, List.of(), List.of(), List.of(), List.of());
        }
        List<Long> ids = list.stream().map(Job::getId).toList();
        Map<Long, Job> jobMap = list.stream().collect(Collectors.toMap(Job::getId, j -> j));
        Map<Integer, List<Application>> byYear = applications.findByJobIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getAppliedAt().getYear(), TreeMap::new, Collectors.toList()));
        List<YearStat> years = new ArrayList<>();
        byYear.forEach((year, apps) -> {
            List<BigDecimal> ctcs = apps.stream().filter(a -> a.getStatus() == Application.Status.SELECTED)
                    .map(a -> jobMap.get(a.getJobId()).getCtcLpa()).filter(Objects::nonNull).toList();
            BigDecimal avg = ctcs.isEmpty() ? null : ctcs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(ctcs.size()), 2, java.math.RoundingMode.HALF_UP);
            years.add(new YearStat(year, apps.size(),
                    apps.stream().filter(a -> a.getStatus() != Application.Status.APPLIED
                            && a.getStatus() != Application.Status.WITHDRAWN).count(),
                    apps.stream().filter(a -> a.getStatus() == Application.Status.SELECTED).count(), avg));
        });
        Job latest = list.stream().max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt())).orElseThrow();
        List<String> pattern = rounds.findByJobIdOrderBySequenceAsc(latest.getId()).stream()
                .map(r -> r.getName() + " (" + r.getType().name().replace('_', ' ').toLowerCase() + ")").toList();
        List<Long> testIds = rounds.findByAssessmentTestIdIsNotNullAndJobIdIn(ids).stream()
                .map(Round::getAssessmentTestId).distinct().toList();
        List<String> skills = list.stream().flatMap(j -> Texts.toList(j.getRequiredSkills()).stream()).distinct().toList();
        return new CompanyStats(latest.getCompanyName(), years, pattern, testIds, skills);
    }
}
