package com.placify.placement.service;

import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.placement.client.PlacementClients;
import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.InterviewSlot;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Round;
import com.placify.placement.domain.RoundResult;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.InterviewSlotRepository;
import com.placify.placement.repository.JobRepository;
import com.placify.placement.repository.RoundRepository;
import com.placify.placement.repository.RoundResultRepository;
import com.placify.placement.web.PlacementDtos.ApplicantView;
import com.placify.placement.web.PlacementDtos.ApplicationView;
import com.placify.placement.web.PlacementDtos.SlotView;
import com.placify.placement.web.PlacementDtos.Timeline;
import com.placify.placement.web.PlacementDtos.TimelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applying, withdrawing, ranking applicants and shortlisting.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applications;
    private final JobRepository jobs;
    private final RoundRepository rounds;
    private final RoundResultRepository results;
    private final InterviewSlotRepository slots;
    private final JobService jobService;
    private final EligibilityService eligibility;
    private final MatchService matchService;
    private final PlacementClients clients;
    private final EventPublisher publisher;

    // ---------------------------------------------------------------- student actions

    @Transactional
    public ApplicationView apply(Long jobId) {
        String studentId = CurrentUser.id();
        Job job = jobService.get(jobId);
        StudentInfo student = clients.student(studentId);

        EligibilityService.EligibilityReport report = eligibility.evaluate(job, student);
        if (!report.eligible()) {
            throw ApiException.badRequest("NOT_ELIGIBLE", "You cannot apply: " + String.join("; ", report.reasons()));
        }
        Application app = applications.findByJobIdAndStudentUserId(jobId, studentId).orElse(null);
        int match = matchService.quickScore(job, student);
        if (app != null) {
            // re-applying after a withdrawal reuses the earlier record
            app.setStatus(Application.Status.APPLIED);
            app.setCurrentRoundId(null);
            app.setStatusNote(null);
            app.setMatchScore(match);
        } else {
            app = applications.save(Application.builder()
                    .jobId(jobId)
                    .studentUserId(studentId)
                    .studentName(student.name())
                    .regNo(student.regNo())
                    .branch(student.branch())
                    .status(Application.Status.APPLIED)
                    .matchScore(match)
                    .build());
        }
        publisher.notifyUser(studentId, "Application submitted",
                "You applied for " + job.getTitle() + " at " + job.getCompanyName() + ".",
                "APPLICATION", "/student/applications", false);
        enterOpenTest(app, job);
        return view(app, job, null);
    }

    /**
     * Drives whose first round is an online test linked to an assessment are "open tests": every
     * eligible applicant goes straight into that round, so the test appears in the student's Tests
     * page right after applying. Other drives wait for the company to shortlist.
     */
    public void enterOpenTest(Application app, Job job) {
        Round first = rounds.findFirstByJobIdOrderBySequenceAsc(job.getId()).orElse(null);
        if (first == null || !first.getType().isTest() || first.getAssessmentTestId() == null) {
            return;
        }
        app.setStatus(Application.Status.SHORTLISTED);
        app.setCurrentRoundId(first.getId());
        RoundResult result = results.findByRoundIdAndStudentUserId(first.getId(), app.getStudentUserId())
                .orElseGet(() -> RoundResult.builder().roundId(first.getId()).applicationId(app.getId())
                        .studentUserId(app.getStudentUserId()).build());
        result.setStatus(RoundResult.Status.PENDING);
        result.setScore(null);
        results.save(result);
        publisher.notifyUser(app.getStudentUserId(), "Online test unlocked: " + job.getCompanyName(),
                "Your " + first.getName() + " for " + job.getTitle() + " is ready. Open Tests to start it"
                        + (first.getScheduledAt() == null ? "." : " before " + first.getScheduledAt().toLocalDate() + "."),
                "TEST", "/student/tests", true);
    }

    @Transactional
    public ApplicationView withdraw(Long applicationId) {
        Application app = get(applicationId);
        if (!app.getStudentUserId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("Not your application");
        }
        if (!app.getStatus().isActive()) {
            throw ApiException.badRequest("This application can no longer be withdrawn");
        }
        app.setStatus(Application.Status.WITHDRAWN);
        app.setCurrentRoundId(null);
        // free any booked interview slot
        slots.findByBookedByOrderByStartTimeAsc(app.getStudentUserId()).stream()
                .filter(s -> s.getJobId().equals(app.getJobId()))
                .forEach(s -> {
                    s.setBookedBy(null);
                    s.setBookedAt(null);
                });
        return view(app, jobService.get(app.getJobId()), null);
    }

    public List<ApplicationView> mine() {
        List<Application> list = applications.findByStudentUserIdOrderByAppliedAtDesc(CurrentUser.id());
        return views(list);
    }

    public Application get(Long id) {
        return applications.findById(id).orElseThrow(() -> ApiException.notFound("Application not found"));
    }

    /** Student (own), the job's company or TPO/Admin. */
    public Application getVisible(Long id) {
        Application app = get(id);
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.STUDENT && !app.getStudentUserId().equals(me.userId())) {
            throw ApiException.forbidden("Not your application");
        }
        if (me.role() == Role.COMPANY) {
            jobService.requireOwnerOrStaff(app.getJobId());
        }
        return app;
    }

    public Timeline timeline(Long applicationId) {
        Application app = getVisible(applicationId);
        Job job = jobService.get(app.getJobId());
        Map<Long, RoundResult> byRound = results.findByApplicationId(app.getId()).stream()
                .collect(Collectors.toMap(RoundResult::getRoundId, Function.identity(), (a, b) -> a));
        List<InterviewSlot> mySlots = slots.findByBookedByOrderByStartTimeAsc(app.getStudentUserId());

        List<TimelineStep> steps = new ArrayList<>();
        for (Round r : rounds.findByJobIdOrderBySequenceAsc(job.getId())) {
            RoundResult rr = byRound.get(r.getId());
            String status = rr == null ? "NOT_REACHED" : rr.getStatus().name();
            SlotView slot = mySlots.stream()
                    .filter(s -> s.getRoundId().equals(r.getId()))
                    .findFirst()
                    .map(s -> new SlotView(s.getId(), s.getJobId(), s.getRoundId(), r.getName(), job.getCompanyName(),
                            job.getTitle(), s.getStartTime(), s.getEndTime(), s.getLocation(), true, true, null))
                    .orElse(null);
            steps.add(new TimelineStep(r.getId(), r.getSequence(), r.getName(), r.getType(), r.getScheduledAt(), status,
                    rr == null ? null : rr.getScore(), rr == null ? null : rr.getRemarks(),
                    rr == null ? null : rr.getUpdatedAt(), r.getAssessmentTestId(), slot));
        }
        return new Timeline(view(app, job, null), steps);
    }

    // ---------------------------------------------------------------- company / TPO

    /**
     * Applicants of a job. With sort=rank they are ordered by
     * 50% match score + 25% latest ATS score + 25% placement readiness score.
     */
    @Transactional
    public List<ApplicantView> applicants(Long jobId, Application.Status status, String sort) {
        Job job = jobService.requireOwnerOrStaff(jobId);
        List<Application> list = status == null ? applications.findByJobId(jobId) : applications.findByJobIdAndStatus(jobId, status);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<String, StudentInfo> students = clients.students(list.stream().map(Application::getStudentUserId).toList())
                .stream().collect(Collectors.toMap(StudentInfo::userId, Function.identity(), (a, b) -> a));
        Map<Long, Round> roundMap = rounds.findByJobIdOrderBySequenceAsc(jobId).stream()
                .collect(Collectors.toMap(Round::getId, Function.identity()));

        List<ApplicantView> out = new ArrayList<>();
        for (Application a : list) {
            StudentInfo s = students.get(a.getStudentUserId());
            Integer ats = s == null ? null : s.latestAtsScore();
            Integer readiness = clients.readiness(a.getStudentUserId()).orElse(null);
            if (s != null && a.getMatchScore() == null) {
                a.setMatchScore(matchService.quickScore(job, s));
            }
            int match = a.getMatchScore() == null ? 0 : a.getMatchScore();
            int rank = (int) Math.round(match * 0.5 + (ats == null ? 0 : ats) * 0.25 + (readiness == null ? 0 : readiness) * 0.25);
            a.setRankScore(rank);
            out.add(new ApplicantView(view(a, job, roundMap.get(a.getCurrentRoundId())),
                    s == null ? null : s.cgpa(),
                    s == null ? 0 : s.backlogs(),
                    s == null ? List.of() : s.skills(),
                    ats, readiness, s != null && s.hasResume()));
        }
        Comparator<ApplicantView> order = "rank".equalsIgnoreCase(sort)
                ? Comparator.comparing((ApplicantView v) -> v.application().rankScore(), Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing((ApplicantView v) -> v.application().appliedAt());
        out.sort(order);
        return out;
    }

    /** Moves applicants into the first round. */
    @Audited(action = "APPLICANTS_SHORTLISTED", target = "#jobId")
    @Transactional
    public int shortlist(Long jobId, List<Long> applicationIds) {
        Job job = jobService.requireOwnerOrStaff(jobId);
        Round first = rounds.findFirstByJobIdOrderBySequenceAsc(jobId)
                .orElseThrow(() -> ApiException.badRequest("Add selection rounds before shortlisting"));
        int count = 0;
        for (Application a : applications.findByIdIn(applicationIds)) {
            if (!a.getJobId().equals(jobId) || a.getStatus() != Application.Status.APPLIED) {
                continue;
            }
            a.setStatus(Application.Status.SHORTLISTED);
            a.setCurrentRoundId(first.getId());
            results.save(RoundResult.builder()
                    .roundId(first.getId())
                    .applicationId(a.getId())
                    .studentUserId(a.getStudentUserId())
                    .status(RoundResult.Status.PENDING)
                    .build());
            publisher.notifyUser(a.getStudentUserId(), "Shortlisted: " + job.getCompanyName(),
                    "You are shortlisted for " + job.getTitle() + ". Next: " + first.getName()
                            + (first.getScheduledAt() == null ? "" : " on " + first.getScheduledAt().toLocalDate()) + ".",
                    "SHORTLISTED", "/student/applications/" + a.getId(), true);
            count++;
        }
        return count;
    }

    @Audited(action = "APPLICANTS_REJECTED", target = "#jobId")
    @Transactional
    public int rejectApplicants(Long jobId, List<Long> applicationIds, String reason) {
        Job job = jobService.requireOwnerOrStaff(jobId);
        int count = 0;
        for (Application a : applications.findByIdIn(applicationIds)) {
            if (!a.getJobId().equals(jobId) || !a.getStatus().isActive()) {
                continue;
            }
            a.setStatus(Application.Status.REJECTED);
            a.setCurrentRoundId(null);
            a.setStatusNote(reason);
            publisher.notifyUser(a.getStudentUserId(), "Application update: " + job.getCompanyName(),
                    "Your application for " + job.getTitle() + " was not taken forward. Keep going - more companies are coming!",
                    "REJECTED", "/student/applications/" + a.getId(), false);
            count++;
        }
        return count;
    }

    /** TPO monitoring view of all applications. */
    public Page<ApplicationView> monitor(Long jobId, Application.Status status, Pageable pageable) {
        Page<Application> page = applications.search(jobId, status, pageable);
        Map<Long, Job> jobMap = jobs.findByIdIn(page.getContent().stream().map(Application::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, Round> roundMap = rounds.findByJobIdIn(jobMap.keySet()).stream()
                .collect(Collectors.toMap(Round::getId, Function.identity()));
        return page.map(a -> view(a, jobMap.get(a.getJobId()), roundMap.get(a.getCurrentRoundId())));
    }

    // ---------------------------------------------------------------- mapping

    List<ApplicationView> views(List<Application> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Job> jobMap = jobs.findByIdIn(list.stream().map(Application::getJobId).distinct().toList())
                .stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, Round> roundMap = rounds.findByJobIdIn(jobMap.keySet()).stream()
                .collect(Collectors.toMap(Round::getId, Function.identity()));
        return list.stream().map(a -> view(a, jobMap.get(a.getJobId()), roundMap.get(a.getCurrentRoundId()))).toList();
    }

    ApplicationView view(Application a, Job job, Round current) {
        Round round = current;
        if (round == null && a.getCurrentRoundId() != null) {
            round = rounds.findById(a.getCurrentRoundId()).orElse(null);
        }
        return new ApplicationView(a.getId(), a.getJobId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompanyName(),
                a.getStudentUserId(), a.getStudentName(), a.getRegNo(), a.getBranch(), a.getStatus(),
                a.getCurrentRoundId(), Optional.ofNullable(round).map(Round::getName).orElse(null),
                a.getMatchScore(), a.getRankScore(), a.getStatusNote(), a.getAppliedAt(), a.getUpdatedAt());
    }
}
