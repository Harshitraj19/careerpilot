package com.placify.placement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.common.audit.Audited;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import com.placify.placement.client.PlacementClients;
import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.PlacementPolicy;
import com.placify.placement.domain.Round;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.JobRepository;
import com.placify.placement.repository.RoundRepository;
import com.placify.placement.web.PlacementDtos.JobRequest;
import com.placify.placement.web.PlacementDtos.JobView;
import com.placify.placement.web.PlacementDtos.RoundRequest;
import com.placify.placement.web.PlacementDtos.RoundView;
import com.placify.placement.web.PlacementDtos.SalaryComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Job lifecycle: company posts -> TPO publishes (or rejects) -> students apply -> closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobs;
    private final RoundRepository rounds;
    private final ApplicationRepository applications;
    private final PolicyService policyService;
    private final EligibilityService eligibility;
    private final MatchService matchService;
    private final PlacementClients clients;
    private final InternalClient internalClient;
    private final EventPublisher publisher;

    public Job get(Long id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job not found"));
    }

    /** The job's company or TPO/Admin; students only for open jobs. */
    public Job getVisible(Long id) {
        Job job = get(id);
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.COMPANY && !job.getCompanyUserId().equals(me.userId())) {
            throw ApiException.forbidden("Not your job");
        }
        if (me.role() == Role.STUDENT && job.getStatus() != Job.Status.OPEN && job.getStatus() != Job.Status.CLOSED) {
            throw ApiException.notFound("Job not found");
        }
        return job;
    }

    public Job requireOwnerOrStaff(Long jobId) {
        Job job = get(jobId);
        UserPrincipal me = CurrentUser.get();
        if (me.isStaff()) {
            return job;
        }
        if (me.role() == Role.COMPANY && job.getCompanyUserId().equals(me.userId())) {
            return job;
        }
        throw ApiException.forbidden("Only the company that posted this job or the placement office can do this");
    }

    // ---------------------------------------------------------------- create / update

    @Transactional
    public Job create(JobRequest req) {
        UserPrincipal me = CurrentUser.get();
        String companyName = internalClient.tryGet(ServiceNames.PROFILE, "/internal/profile/companies/" + me.userId(), Map.class)
                .map(m -> (String) m.get("companyName"))
                .orElse(me.name());
        PlacementPolicy policy = policyService.get();
        Job job = Job.builder()
                .companyUserId(me.userId())
                .companyName(companyName == null ? "Company" : companyName)
                .status(Job.Status.PENDING_APPROVAL)
                .build();
        apply(job, req);
        job.setTier(policy.tierFor(job.getCtcLpa()));
        job = jobs.save(job);
        if (req.rounds() != null) {
            int seq = 1;
            for (RoundRequest r : req.rounds()) {
                rounds.save(toRound(job.getId(), seq++, r));
            }
        }
        publisher.notifyUsers(List.of("ROLE:TPO", "ROLE:ADMIN"), "Job awaiting approval",
                job.getCompanyName() + " posted \"" + job.getTitle() + "\". Review and publish it.",
                "JOB_APPROVAL", "/tpo/jobs", false);
        return job;
    }

    @Transactional
    public Job update(Long id, JobRequest req) {
        Job job = requireOwnerOrStaff(id);
        if (job.getStatus() == Job.Status.CLOSED) {
            throw ApiException.badRequest("A closed job cannot be edited");
        }
        apply(job, req);
        job.setTier(policyService.get().tierFor(job.getCtcLpa()));
        if (job.getStatus() == Job.Status.REJECTED) {
            job.setStatus(Job.Status.PENDING_APPROVAL);
        }
        return job;
    }

    private void apply(Job job, JobRequest req) {
        job.setTitle(req.title().trim());
        job.setDescription(req.description());
        job.setRequiredSkills(req.requiredSkills() == null ? null : Texts.join(req.requiredSkills()));
        job.setLocation(req.location());
        job.setJobType(req.jobType());
        job.setCtcLpa(req.ctcLpa());
        job.setStipend(req.stipend());
        job.setMinCgpa(req.minCgpa());
        job.setAllowedBranches(req.allowedBranches() == null ? null : Texts.join(req.allowedBranches()));
        job.setMaxBacklogs(req.maxBacklogs());
        job.setEligibleBatch(req.eligibleBatch());
        job.setMinTenth(req.minTenth());
        job.setMinTwelfth(req.minTwelfth());
        job.setApplicationDeadline(req.applicationDeadline());
        job.setCtcBreakdown(writeBreakdown(req.ctcBreakdown()));
    }

    private Round toRound(Long jobId, int sequence, RoundRequest r) {
        return Round.builder()
                .jobId(jobId)
                .sequence(sequence)
                .name(r.name().trim())
                .type(r.type())
                .scheduledAt(r.scheduledAt())
                .description(r.description())
                .cutoffPercent(r.cutoffPercent())
                .build();
    }

    // ---------------------------------------------------------------- TPO decisions

    @Audited(action = "JOB_PUBLISHED", target = "#id")
    @Transactional
    public Job publish(Long id, Job.Tier tierOverride) {
        Job job = get(id);
        if (job.getStatus() == Job.Status.OPEN) {
            throw ApiException.badRequest("Job is already published");
        }
        if (rounds.findByJobIdOrderBySequenceAsc(id).isEmpty()) {
            throw ApiException.badRequest("Add at least one selection round before publishing");
        }
        job.setStatus(Job.Status.OPEN);
        job.setReviewNote(null);
        job.setPublishedAt(LocalDateTime.now());
        if (tierOverride != null) {
            job.setTier(tierOverride);
        }
        publisher.notifyUser(job.getCompanyUserId(), "Job published",
                "\"" + job.getTitle() + "\" is now live for students.", "JOB", "/company/jobs/" + id, true);
        notifyEligibleStudents(job);
        return job;
    }

    @Audited(action = "JOB_REJECTED", target = "#id")
    @Transactional
    public Job reject(Long id, String note) {
        Job job = get(id);
        job.setStatus(Job.Status.REJECTED);
        job.setReviewNote(note);
        publisher.notifyUser(job.getCompanyUserId(), "Job needs changes",
                "\"" + job.getTitle() + "\" was not published." + (Texts.isBlank(note) ? "" : " Note: " + note),
                "JOB", "/company/jobs/" + id, true);
        return job;
    }

    @Audited(action = "JOB_CLOSED", target = "#id")
    @Transactional
    public Job close(Long id) {
        Job job = requireOwnerOrStaff(id);
        job.setStatus(Job.Status.CLOSED);
        return job;
    }

    /** Sends an in-app + email alert to every student who meets the academic criteria. */
    private void notifyEligibleStudents(Job job) {
        try {
            List<String> ids = clients.allStudents(job.getEligibleBatch()).stream()
                    .filter(s -> eligibility.academicallyEligible(job, s))
                    .map(StudentInfo::userId)
                    .toList();
            String ctc = job.getCtcLpa() == null ? "" : " (" + job.getCtcLpa() + " LPA)";
            for (int i = 0; i < ids.size(); i += 200) {
                publisher.notifyUsers(ids.subList(i, Math.min(ids.size(), i + 200)),
                        "New job: " + job.getCompanyName(),
                        "You are eligible for " + job.getTitle() + " at " + job.getCompanyName() + ctc + ". Apply now!",
                        "NEW_JOB", "/student/jobs/" + job.getId(), true);
            }
            log.info("Job {} published; {} eligible students notified", job.getId(), ids.size());
        } catch (Exception ex) {
            log.warn("Could not notify eligible students for job {}: {}", job.getId(), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- rounds

    public List<Round> rounds(Long jobId) {
        return rounds.findByJobIdOrderBySequenceAsc(jobId);
    }

    @Transactional
    public Round addRound(Long jobId, RoundRequest req) {
        requireOwnerOrStaff(jobId);
        int next = rounds.findByJobIdOrderBySequenceAsc(jobId).stream().mapToInt(Round::getSequence).max().orElse(0) + 1;
        return rounds.save(toRound(jobId, next, req));
    }

    @Transactional
    public Round updateRound(Long roundId, RoundRequest req) {
        Round r = rounds.findById(roundId).orElseThrow(() -> ApiException.notFound("Round not found"));
        requireOwnerOrStaff(r.getJobId());
        r.setName(req.name().trim());
        r.setType(req.type());
        r.setScheduledAt(req.scheduledAt());
        r.setDescription(req.description());
        r.setCutoffPercent(req.cutoffPercent());
        return r;
    }

    @Transactional
    public void deleteRound(Long roundId) {
        Round r = rounds.findById(roundId).orElseThrow(() -> ApiException.notFound("Round not found"));
        requireOwnerOrStaff(r.getJobId());
        boolean inUse = applications.findByJobId(r.getJobId()).stream()
                .anyMatch(a -> roundId.equals(a.getCurrentRoundId()));
        if (inUse) {
            throw ApiException.badRequest("Candidates are already in this round; it cannot be deleted");
        }
        rounds.delete(r);
    }

    // ---------------------------------------------------------------- views

    public List<JobView> listForCurrentUser(Job.Status status, String q) {
        UserPrincipal me = CurrentUser.get();
        List<Job> list;
        if (me.role() == Role.COMPANY) {
            list = jobs.findByCompanyUserIdOrderByCreatedAtDesc(me.userId());
        } else if (me.role() == Role.STUDENT) {
            list = jobs.findByStatusOrderByPublishedAtDesc(Job.Status.OPEN);
        } else {
            list = status == null ? jobs.findAll() : jobs.findByStatusOrderByPublishedAtDesc(status);
            list = list.stream().sorted(Comparator.comparing(Job::getCreatedAt).reversed()).toList();
        }
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            list = list.stream().filter(j -> j.getTitle().toLowerCase().contains(query)
                    || j.getCompanyName().toLowerCase().contains(query)
                    || (j.getRequiredSkills() != null && j.getRequiredSkills().toLowerCase().contains(query))).toList();
        }
        return toViews(list, me);
    }

    public JobView view(Job job) {
        return toViews(List.of(job), CurrentUser.get()).get(0);
    }

    private List<JobView> toViews(List<Job> list, UserPrincipal me) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Job::getId).toList();
        Map<Long, List<Round>> roundsByJob = rounds.findByJobIdIn(ids).stream()
                .sorted(Comparator.comparingInt(Round::getSequence))
                .collect(Collectors.groupingBy(Round::getJobId));
        Map<Long, Long> counts = applications.findByJobIdIn(ids).stream()
                .collect(Collectors.groupingBy(Application::getJobId, Collectors.counting()));

        StudentInfo student = null;
        Map<Long, Application> mine = Map.of();
        if (me.role() == Role.STUDENT) {
            student = clients.student(me.userId());
            mine = applications.findByStudentUserIdOrderByAppliedAtDesc(me.userId()).stream()
                    .collect(Collectors.toMap(Application::getJobId, Function.identity(), (a, b) -> a));
        }

        List<JobView> views = new ArrayList<>();
        for (Job j : list) {
            Boolean eligible = null;
            List<String> reasons = null;
            Integer match = null;
            Application.Status myStatus = null;
            if (student != null) {
                EligibilityService.EligibilityReport report = eligibility.evaluate(j, student);
                eligible = report.eligible();
                reasons = report.reasons();
                match = matchService.quickScore(j, student);
                myStatus = Optional.ofNullable(mine.get(j.getId())).map(Application::getStatus).orElse(null);
            }
            boolean showCount = me.role() != Role.STUDENT;
            views.add(new JobView(j.getId(), j.getCompanyUserId(), j.getCompanyName(), j.getTitle(), j.getDescription(),
                    Texts.toList(j.getRequiredSkills()), j.getLocation(), j.getJobType(), j.getCtcLpa(), j.getStipend(),
                    readBreakdown(j.getCtcBreakdown()), j.getTier(), j.getStatus(), j.getMinCgpa(), Texts.toList(j.getAllowedBranches()), j.getMaxBacklogs(),
                    j.getEligibleBatch(), j.getMinTenth(), j.getMinTwelfth(), j.getApplicationDeadline(),
                    me.role() == Role.STUDENT ? null : j.getReviewNote(), j.getPublishedAt(), j.getCreatedAt(),
                    roundsByJob.getOrDefault(j.getId(), List.of()).stream().map(RoundView::of).toList(),
                    showCount ? counts.getOrDefault(j.getId(), 0L) : null,
                    eligible, reasons, myStatus, match));
        }
        return views;
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<SalaryComponent>> BREAKDOWN = new TypeReference<>() {
    };

    static String writeBreakdown(List<SalaryComponent> lines) {
        if (lines == null) {
            return null;
        }
        List<SalaryComponent> clean = lines.stream().filter(l -> l != null && !Texts.isBlank(l.component())).toList();
        try {
            return clean.isEmpty() ? null : JSON.writeValueAsString(clean);
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("Invalid salary breakdown");
        }
    }

    static List<SalaryComponent> readBreakdown(String json) {
        if (Texts.isBlank(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, BREAKDOWN);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    public List<Long> idsOfCompany(String companyUserId) {
        return jobs.findByCompanyUserIdOrderByCreatedAtDesc(companyUserId).stream().map(Job::getId).filter(Objects::nonNull).toList();
    }
}
