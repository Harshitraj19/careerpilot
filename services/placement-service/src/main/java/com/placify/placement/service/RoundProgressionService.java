package com.placify.placement.service;

import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Round;
import com.placify.placement.domain.RoundResult;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.RoundRepository;
import com.placify.placement.repository.RoundResultRepository;
import com.placify.placement.web.PlacementDtos.ResultUpdate;
import com.placify.placement.web.PlacementDtos.RoundResultView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Multi-round selection with automatic progression:
 * <ul>
 *   <li>CLEARED  -> the candidate moves to the next round, or is SELECTED after the last one</li>
 *   <li>REJECTED / ABSENT -> the application is rejected</li>
 *   <li>UNDER_REVIEW -> waits for a human decision (e.g. proctoring flag)</li>
 * </ul>
 * Results come from the company/TPO (interviews) or automatically from tests
 * and interview evaluations (RabbitMQ events).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundProgressionService {

    private final RoundRepository rounds;
    private final RoundResultRepository results;
    private final ApplicationRepository applications;
    private final JobService jobService;
    private final EventPublisher publisher;

    public Round round(Long id) {
        return rounds.findById(id).orElseThrow(() -> ApiException.notFound("Round not found"));
    }

    public List<RoundResultView> results(Long roundId) {
        Round round = round(roundId);
        jobService.requireOwnerOrStaff(round.getJobId());
        List<RoundResult> list = results.findByRoundId(roundId);
        Map<Long, Application> apps = applications.findByIdIn(list.stream().map(RoundResult::getApplicationId).toList())
                .stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        return list.stream().map(r -> {
            Application a = apps.get(r.getApplicationId());
            return new RoundResultView(r.getId(), r.getRoundId(), r.getApplicationId(), r.getStudentUserId(),
                    a == null ? null : a.getStudentName(), a == null ? null : a.getRegNo(), r.getStatus(), r.getScore(),
                    r.getRemarks(), r.getUpdatedAt());
        }).toList();
    }

    /** Bulk decision by the company or TPO. */
    @Audited(action = "ROUND_RESULTS_UPDATED", target = "#roundId")
    @Transactional
    public int decide(Long roundId, ResultUpdate req) {
        if (req.status() == RoundResult.Status.PENDING) {
            throw ApiException.badRequest("Choose CLEARED, REJECTED, ABSENT or UNDER_REVIEW");
        }
        Round round = round(roundId);
        Job job = jobService.requireOwnerOrStaff(round.getJobId());
        UserPrincipal me = CurrentUser.get();
        int count = 0;
        for (Long appId : req.applicationIds()) {
            Optional<RoundResult> rr = results.findByRoundIdAndApplicationId(roundId, appId);
            if (rr.isEmpty()) {
                continue;
            }
            if (apply(job, round, rr.get(), req.status(), req.score(), req.remarks(), me.userId())) {
                count++;
            }
        }
        return count;
    }

    /** Called from the assessment-completed event. */
    @Transactional
    public void onTestCompleted(Long roundId, String studentUserId, double percent, boolean passed, boolean flagged, Integer trustScore) {
        Optional<Round> round = rounds.findById(roundId);
        Optional<RoundResult> rr = results.findByRoundIdAndStudentUserId(roundId, studentUserId);
        if (round.isEmpty() || rr.isEmpty()) {
            log.warn("No round result for round {} / student {}", roundId, studentUserId);
            return;
        }
        Job job = jobService.get(round.get().getJobId());
        BigDecimal score = BigDecimal.valueOf(Math.round(percent * 100) / 100.0);
        if (flagged) {
            apply(job, round.get(), rr.get(), RoundResult.Status.UNDER_REVIEW, score,
                    "Scored " + score + "%. Proctoring flagged this attempt (trust score " + trustScore
                            + "). Review the proctoring report before deciding.", "system");
            publisher.notifyUser(job.getCompanyUserId(), "Test attempt needs review",
                    "An attempt in " + round.get().getName() + " for " + job.getTitle() + " was flagged by proctoring.",
                    "PROCTORING", "/company/jobs/" + job.getId(), false);
            return;
        }
        apply(job, round.get(), rr.get(), passed ? RoundResult.Status.CLEARED : RoundResult.Status.REJECTED, score,
                "Auto-evaluated test: " + score + "%" + (round.get().getCutoffPercent() == null ? "" :
                        " (cut-off " + round.get().getCutoffPercent() + "%)"), "system");
    }

    /** Called from the interview-evaluated event. */
    @Transactional
    public void onInterviewEvaluated(Long roundId, String studentUserId, String result, Integer score, String feedback) {
        Optional<Round> round = rounds.findById(roundId);
        Optional<RoundResult> rr = results.findByRoundIdAndStudentUserId(roundId, studentUserId);
        if (round.isEmpty() || rr.isEmpty()) {
            return;
        }
        RoundResult.Status status;
        try {
            status = RoundResult.Status.valueOf(result);
        } catch (Exception ex) {
            return;
        }
        Job job = jobService.get(round.get().getJobId());
        apply(job, round.get(), rr.get(), status, score == null ? null : BigDecimal.valueOf(score), feedback, "interviewer");
    }

    /**
     * Applies one decision. Only the candidate's current round can be decided.
     * @return true if something changed
     */
    private boolean apply(Job job, Round round, RoundResult rr, RoundResult.Status status, BigDecimal score,
                          String remarks, String decidedBy) {
        Application app = applications.findById(rr.getApplicationId()).orElse(null);
        if (app == null || !app.getStatus().isActive() || !round.getId().equals(app.getCurrentRoundId())) {
            return false;
        }
        if (!EnumSet.of(RoundResult.Status.PENDING, RoundResult.Status.UNDER_REVIEW).contains(rr.getStatus())) {
            return false;
        }
        rr.setStatus(status);
        rr.setScore(score);
        rr.setRemarks(remarks);
        rr.setDecidedBy(decidedBy);

        switch (status) {
            case CLEARED -> advance(job, round, app);
            case REJECTED, ABSENT -> {
                app.setStatus(Application.Status.REJECTED);
                app.setCurrentRoundId(null);
                app.setStatusNote("Not cleared: " + round.getName());
                publisher.notifyUser(app.getStudentUserId(), "Result: " + round.getName(),
                        "You did not clear " + round.getName() + " for " + job.getTitle() + " at " + job.getCompanyName()
                                + ". Review the feedback and keep preparing.",
                        "ROUND_RESULT", "/student/applications/" + app.getId(), true);
            }
            case UNDER_REVIEW -> app.setStatus(Application.Status.IN_PROCESS);
            default -> {
            }
        }
        return true;
    }

    private void advance(Job job, Round round, Application app) {
        Optional<Round> next = rounds.findFirstByJobIdAndSequenceGreaterThanOrderBySequenceAsc(job.getId(), round.getSequence());
        if (next.isPresent()) {
            app.setStatus(Application.Status.IN_PROCESS);
            app.setCurrentRoundId(next.get().getId());
            results.save(RoundResult.builder()
                    .roundId(next.get().getId())
                    .applicationId(app.getId())
                    .studentUserId(app.getStudentUserId())
                    .status(RoundResult.Status.PENDING)
                    .build());
            publisher.notifyUser(app.getStudentUserId(), "Cleared " + round.getName() + "!",
                    "You cleared " + round.getName() + " for " + job.getTitle() + " at " + job.getCompanyName()
                            + ". Next round: " + next.get().getName() + ".",
                    "ROUND_RESULT", "/student/applications/" + app.getId(), true);
        } else {
            app.setStatus(Application.Status.SELECTED);
            app.setCurrentRoundId(null);
            app.setStatusNote("Selected");
            publisher.notifyUser(app.getStudentUserId(), "Selected at " + job.getCompanyName() + "!",
                    "Congratulations! You cleared all rounds for " + job.getTitle() + ". Your offer will follow.",
                    "SELECTED", "/student/applications/" + app.getId(), true);
            publisher.notifyUser(job.getCompanyUserId(), "Candidate selected",
                    app.getStudentName() + " (" + app.getRegNo() + ") cleared all rounds for " + job.getTitle()
                            + ". Issue the offer letter.", "SELECTED", "/company/jobs/" + job.getId(), false);
        }
    }

    @Transactional
    public Round linkTest(Long roundId, Long testId) {
        Round round = round(roundId);
        jobService.requireOwnerOrStaff(round.getJobId());
        if (!round.getType().isTest()) {
            throw ApiException.badRequest("Only test rounds can be linked to an assessment");
        }
        round.setAssessmentTestId(testId);
        return round;
    }
}
