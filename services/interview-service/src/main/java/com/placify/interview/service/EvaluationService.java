package com.placify.interview.service;

import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.interview.client.InterviewClients;
import com.placify.interview.client.InterviewClients.RoundInfo;
import com.placify.interview.client.InterviewClients.StudentSummary;
import com.placify.interview.domain.InterviewEvaluation;
import com.placify.interview.repository.InterviewEvaluationRepository;
import com.placify.interview.web.InterviewDtos.CandidateView;
import com.placify.interview.web.InterviewDtos.EvaluationRequest;
import com.placify.interview.web.InterviewDtos.EvaluationView;
import com.placify.interview.web.InterviewDtos.MyInterviewResult;
import com.placify.interview.web.InterviewDtos.RoundBoard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interview scorecards. The company that owns the job (or the TPO) fills a
 * scorecard for each candidate; a final result (cleared / rejected / absent)
 * is sent to the placement service, which moves the candidate on.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final InterviewEvaluationRepository evaluations;
    private final InterviewClients clients;
    private final EventPublisher publisher;

    public RoundBoard board(Long roundId) {
        RoundInfo round = ownedRound(roundId);
        List<InterviewEvaluation> done = evaluations.findByRoundIdOrderByOverallScoreDesc(roundId);
        List<String> pendingIds = clients.candidates(roundId);

        Set<String> ids = new LinkedHashSet<>(pendingIds);
        done.forEach(e -> ids.add(e.getStudentUserId()));
        Map<String, StudentSummary> students = clients.students(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(StudentSummary::userId, Function.identity(), (a, b) -> a));
        Map<String, InterviewEvaluation> byStudent = done.stream()
                .collect(Collectors.toMap(InterviewEvaluation::getStudentUserId, Function.identity(), (a, b) -> a));

        List<CandidateView> pending = pendingIds.stream().map(id -> {
            StudentSummary s = students.get(id);
            InterviewEvaluation e = byStudent.get(id);
            return new CandidateView(id, s == null ? null : s.name(), s == null ? null : s.regNo(),
                    s == null ? null : s.branch(), s == null ? null : s.cgpa(), s == null ? List.of() : s.skills(),
                    s == null ? null : s.bestAtsScore(), e == null ? null : view(e, s));
        }).toList();
        List<EvaluationView> evaluated = done.stream().map(e -> view(e, students.get(e.getStudentUserId()))).toList();
        return new RoundBoard(round.roundId(), round.name(), round.type(), round.jobTitle(), pending, evaluated);
    }

    @Transactional
    @Audited(action = "INTERVIEW_EVALUATED", target = "#req.studentUserId()")
    public EvaluationView evaluate(EvaluationRequest req) {
        RoundInfo round = ownedRound(req.roundId());
        if (!round.isInterviewLike()) {
            throw ApiException.badRequest("Scorecards are only used for interview / discussion rounds; tests are graded automatically");
        }
        InterviewEvaluation e = evaluations.findByRoundIdAndStudentUserId(req.roundId(), req.studentUserId()).orElse(null);
        boolean finalBefore = e != null && e.getResult() != InterviewEvaluation.Result.UNDER_REVIEW;
        if (finalBefore) {
            throw ApiException.conflict("A final result was already recorded for this candidate");
        }
        if (!clients.isCandidate(req.roundId(), req.studentUserId())) {
            throw ApiException.badRequest("This student is not waiting in this round");
        }
        if (e == null) {
            e = InterviewEvaluation.builder()
                    .jobId(round.jobId())
                    .roundId(round.roundId())
                    .studentUserId(req.studentUserId())
                    .companyUserId(round.companyUserId())
                    .build();
        }
        e.setInterviewerName(req.interviewerName() == null || req.interviewerName().isBlank()
                ? CurrentUser.get().name() : req.interviewerName().trim());
        e.setTechnical(req.technical());
        e.setProblemSolving(req.problemSolving());
        e.setCommunication(req.communication());
        e.setCultureFit(req.cultureFit());
        e.setOverallScore(InterviewEvaluation.overall(req.technical(), req.problemSolving(), req.communication(), req.cultureFit()));
        e.setResult(req.result());
        e.setStrengths(req.strengths());
        e.setConcerns(req.concerns());
        e.setFeedback(req.feedback());
        e.setShareFeedback(req.shareFeedback());
        e = evaluations.save(e);

        if (req.result() != InterviewEvaluation.Result.UNDER_REVIEW) {
            publisher.publish(RoutingKeys.INTERVIEW_EVALUATED, new Events.InterviewEvaluatedEvent(round.jobId(),
                    round.roundId(), req.studentUserId(), req.result().name(), e.getOverallScore(),
                    req.shareFeedback() ? req.feedback() : null));
        }
        return view(e, null);
    }

    public List<MyInterviewResult> mine() {
        return evaluations.findByStudentUserIdOrderByCreatedAtDesc(CurrentUser.id()).stream()
                .filter(e -> e.getResult() != InterviewEvaluation.Result.UNDER_REVIEW)
                .map(e -> new MyInterviewResult(e.getRoundId(), e.getJobId(), e.getOverallScore(), e.getResult(),
                        e.isShareFeedback() ? e.getFeedback() : null, e.getUpdatedAt()))
                .toList();
    }

    /** The calling company must own the job of this round; TPO and admin may act for any company. */
    private RoundInfo ownedRound(Long roundId) {
        RoundInfo round = clients.round(roundId);
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.COMPANY && !me.userId().equals(round.companyUserId())) {
            throw ApiException.forbidden("This round belongs to another company");
        }
        if (me.role() == Role.STUDENT) {
            throw ApiException.forbidden("Not allowed");
        }
        return round;
    }

    private static EvaluationView view(InterviewEvaluation e, StudentSummary s) {
        return EvaluationView.of(e, s == null ? null : s.name(), s == null ? null : s.regNo());
    }
}
