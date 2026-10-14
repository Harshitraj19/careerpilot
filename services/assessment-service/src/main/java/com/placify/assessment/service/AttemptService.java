package com.placify.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.assessment.client.AssessmentClients;
import com.placify.assessment.domain.Attempt;
import com.placify.assessment.domain.AttemptQuestion;
import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.domain.Test;
import com.placify.assessment.domain.TestPoolItem;
import com.placify.assessment.repository.AttemptQuestionRepository;
import com.placify.assessment.repository.AttemptRepository;
import com.placify.assessment.repository.McqQuestionRepository;
import com.placify.assessment.repository.TestPoolItemRepository;
import com.placify.assessment.web.AssessmentDtos.AnswerRequest;
import com.placify.assessment.web.AssessmentDtos.AttemptQuestionView;
import com.placify.assessment.web.AssessmentDtos.AttemptResult;
import com.placify.assessment.web.AssessmentDtos.AttemptView;
import com.placify.assessment.web.AssessmentDtos.CodingStatement;
import com.placify.assessment.web.AssessmentDtos.ProctoringSettings;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Taking a test: start (random question set and option order per student),
 * answer, submit, automatic grading and publishing the result to placement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final AttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final TestPoolItemRepository pool;
    private final McqQuestionRepository mcqs;
    private final TestService testService;
    private final McqService mcqService;
    private final AssessmentClients clients;
    private final EventPublisher publisher;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    @Value("${placify.assessment.min-trust-score:60}")
    private int minTrustScore;

    // ---------------------------------------------------------------- start

    @Transactional
    public AttemptView start(Long testId) {
        UserPrincipal me = CurrentUser.get();
        Test test = testService.get(testId);
        Optional<Attempt> existing = attempts.findByTestIdAndStudentUserId(testId, me.userId());
        if (existing.isPresent()) {
            if (existing.get().getStatus().isFinished()) {
                throw ApiException.badRequest("You have already completed this test");
            }
            return view(existing.get(), test);
        }
        if (!test.isOpenNow()) {
            throw ApiException.badRequest(test.getWindowStart().isAfter(LocalDateTime.now())
                    ? "The test opens at " + test.getWindowStart().toLocalTime() + " on " + test.getWindowStart().toLocalDate()
                    : "This test is closed");
        }
        if (test.getRoundId() != null && !clients.isRoundCandidate(test.getRoundId(), me.userId())) {
            throw ApiException.forbidden("This test is only for candidates in the current round");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusMinutes(test.getDurationMinutes());
        if (deadline.isAfter(test.getWindowEnd())) {
            deadline = test.getWindowEnd(); // the window end is a hard stop
        }
        Attempt attempt;
        try {
            attempt = attempts.saveAndFlush(Attempt.builder()
                    .testId(testId)
                    .studentUserId(me.userId())
                    .studentName(me.name())
                    .studentLogin(me.loginId())
                    .status(Attempt.Status.IN_PROGRESS)
                    .startedAt(now)
                    .deadlineAt(deadline)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("The test was already started in another tab. Please refresh.");
        }
        buildQuestionSet(attempt, test);
        return view(attempt, test);
    }

    /** Picks a random subset of the pools and shuffles question and option order. */
    private void buildQuestionSet(Attempt attempt, Test test) {
        List<TestPoolItem> mcqPool = new ArrayList<>(pool.findByTestIdAndKind(test.getId(), TestPoolItem.Kind.MCQ));
        List<TestPoolItem> codingPool = new ArrayList<>(pool.findByTestIdAndKind(test.getId(), TestPoolItem.Kind.CODING));
        if (test.isRandomize()) {
            Collections.shuffle(mcqPool, random);
            Collections.shuffle(codingPool, random);
        }
        List<TestPoolItem> chosenMcq = mcqPool.subList(0, Math.min(test.getMcqCount(), mcqPool.size()));
        List<TestPoolItem> chosenCoding = codingPool.subList(0, Math.min(test.getCodingCount(), codingPool.size()));

        Map<Long, McqQuestion> mcqMap = mcqs.findByIdIn(chosenMcq.stream().map(i -> Long.valueOf(i.getRefId())).toList())
                .stream().collect(Collectors.toMap(McqQuestion::getId, Function.identity()));
        int order = 0;
        for (TestPoolItem item : chosenMcq) {
            McqQuestion m = mcqMap.get(Long.valueOf(item.getRefId()));
            if (m == null) {
                continue;
            }
            List<Integer> optionOrder = IntStream.range(0, mcqService.options(m).size()).boxed().collect(Collectors.toList());
            if (test.isRandomize()) {
                Collections.shuffle(optionOrder, random);
            }
            attemptQuestions.save(AttemptQuestion.builder()
                    .attemptId(attempt.getId()).kind(TestPoolItem.Kind.MCQ).refId(item.getRefId()).orderIndex(order++)
                    .optionOrder(json(optionOrder)).maxMarks(BigDecimal.valueOf(test.getMarksPerMcq())).build());
        }
        for (TestPoolItem item : chosenCoding) {
            attemptQuestions.save(AttemptQuestion.builder()
                    .attemptId(attempt.getId()).kind(TestPoolItem.Kind.CODING).refId(item.getRefId()).orderIndex(order++)
                    .maxMarks(BigDecimal.valueOf(item.getPoints())).build());
        }
    }

    // ---------------------------------------------------------------- answering

    public AttemptView get(Long attemptId) {
        Attempt a = mine(attemptId);
        return view(a, testService.get(a.getTestId()));
    }

    @Transactional
    public void answer(Long attemptId, AnswerRequest req) {
        Attempt a = mine(attemptId);
        ensureRunning(a);
        AttemptQuestion q = attemptQuestions.findById(req.attemptQuestionId())
                .filter(x -> x.getAttemptId().equals(attemptId) && x.getKind() == TestPoolItem.Kind.MCQ)
                .orElseThrow(() -> ApiException.notFound("Question not found in this attempt"));
        List<Integer> order = optionOrder(q);
        if (req.selectedIndex() >= order.size()) {
            throw ApiException.badRequest("Invalid option");
        }
        q.setSelectedIndex(order.get(req.selectedIndex())); // store the ORIGINAL option index
    }

    /** Problem statement of a coding question in the student's attempt (public test cases only). */
    public CodingStatement codingStatement(Long attemptId, String questionId) {
        Attempt a = mine(attemptId);
        attemptQuestions.findFirstByAttemptIdAndRefId(a.getId(), questionId)
                .orElseThrow(() -> ApiException.notFound("This problem is not part of your test"));
        AssessmentClients.CodingQuestion q = clients.codingQuestion(questionId);
        List<Map<String, Object>> samples = clients.codingTestcases(questionId).stream()
                .filter(t -> !t.hidden())
                .map(t -> Map.<String, Object>of("input", t.input(), "expectedOutput", t.expectedOutput()))
                .toList();
        return new CodingStatement(q.id(), q.title(), q.description(), q.inputFormat(), q.outputFormat(), q.constraints(),
                q.sampleInput(), q.sampleOutput(), q.timeLimit(), q.memoryLimit(), samples);
    }

    @Transactional
    public AttemptResult submit(Long attemptId) {
        Attempt a = mine(attemptId);
        if (a.getStatus().isFinished()) {
            return result(a, true);
        }
        finish(a, Attempt.Status.SUBMITTED, null);
        return result(a, true);
    }

    // ---------------------------------------------------------------- grading

    /** Grades the attempt and sends the result to placement (and analytics). */
    void finish(Attempt a, Attempt.Status status, String reason) {
        Test test = testService.get(a.getTestId());
        List<AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByOrderIndexAsc(a.getId());
        Map<Long, McqQuestion> mcqMap = mcqs.findByIdIn(qs.stream().filter(q -> q.getKind() == TestPoolItem.Kind.MCQ)
                        .map(q -> Long.valueOf(q.getRefId())).toList())
                .stream().collect(Collectors.toMap(McqQuestion::getId, Function.identity()));

        BigDecimal mcqScore = BigDecimal.ZERO;
        BigDecimal codingScore = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        for (AttemptQuestion q : qs) {
            max = max.add(q.getMaxMarks());
            if (q.getKind() == TestPoolItem.Kind.MCQ) {
                McqQuestion m = mcqMap.get(Long.valueOf(q.getRefId()));
                boolean correct = m != null && q.getSelectedIndex() != null && q.getSelectedIndex() == m.getCorrectIndex();
                q.setCorrect(q.getSelectedIndex() == null ? null : correct);
                q.setMarks(correct ? q.getMaxMarks() : BigDecimal.ZERO);
                mcqScore = mcqScore.add(q.getMarks());
            } else {
                BigDecimal marks = q.getMarks() == null ? BigDecimal.ZERO : q.getMarks();
                q.setMarks(marks);
                codingScore = codingScore.add(marks);
            }
        }
        BigDecimal total = mcqScore.add(codingScore);
        BigDecimal percent = max.signum() == 0 ? BigDecimal.ZERO
                : total.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP);

        a.setStatus(status);
        a.setTerminationReason(reason);
        a.setSubmittedAt(LocalDateTime.now());
        a.setMcqScore(mcqScore);
        a.setCodingScore(codingScore);
        a.setTotalScore(total);
        a.setMaxScore(max);
        a.setPercent(percent);
        a.setPassed(percent.compareTo(test.getCutoffPercent()) >= 0);

        if (test.isProctored()) {
            Optional<AssessmentClients.ProctoringSummary> p = clients.proctoring(a.getId());
            a.setTrustScore(p.map(AssessmentClients.ProctoringSummary::trustScore).orElse(null));
            a.setProctoringFlagged(status == Attempt.Status.TERMINATED
                    || p.map(s -> s.flagged() || (s.trustScore() != null && s.trustScore() < minTrustScore)).orElse(false));
        }

        publisher.publish(RoutingKeys.ASSESSMENT_COMPLETED, new Events.AssessmentCompletedEvent(
                a.getId(), test.getId(), test.getJobId(), test.getRoundId(), a.getStudentUserId(), percent.doubleValue(),
                Boolean.TRUE.equals(a.getPassed()), a.getTrustScore(), a.isProctoringFlagged()));
        String msg = test.getRoundId() == null
                ? "You scored " + percent + "% in " + test.getTitle() + "."
                : "Your " + test.getTitle() + " has been submitted. Results will appear in your application timeline.";
        publisher.notifyUser(a.getStudentUserId(), "Test submitted", msg, "TEST", "/student/tests", false);
    }

    /** A coding submission inside a test was judged: keep the best score for that problem. */
    @Transactional
    public void onSubmissionJudged(Events.SubmissionJudgedEvent e) {
        if (e.attemptId() == null) {
            return;
        }
        Attempt a = attempts.findById(e.attemptId()).orElse(null);
        if (a == null || !a.getStudentUserId().equals(e.userId())) {
            return;
        }
        AttemptQuestion q = attemptQuestions.findFirstByAttemptIdAndRefId(a.getId(), e.questionId()).orElse(null);
        if (q == null || q.getKind() != TestPoolItem.Kind.CODING || e.total() == 0) {
            return;
        }
        BigDecimal marks = q.getMaxMarks().multiply(BigDecimal.valueOf(e.passed()))
                .divide(BigDecimal.valueOf(e.total()), 2, RoundingMode.HALF_UP);
        if (q.getMarks() == null || marks.compareTo(q.getMarks()) > 0) {
            q.setMarks(marks);
            q.setCodingPassed(e.passed());
            q.setCodingTotal(e.total());
            q.setBestSubmissionId(e.submissionId());
        }
        // a submission judged after the attempt was graded (still running in Judge0) updates the score
        if (a.getStatus().isFinished()) {
            log.info("Late coding result for finished attempt {}; score kept as graded", a.getId());
        }
    }

    /** Proctoring decided to end the attempt (too many violations). */
    @Transactional
    public void onProctoringTerminate(Events.ProctoringTerminateEvent e) {
        attempts.findById(e.attemptId()).filter(a -> !a.getStatus().isFinished()).ifPresent(a -> {
            finish(a, Attempt.Status.TERMINATED, e.reason());
            publisher.notifyUser(a.getStudentUserId(), "Test ended by proctoring",
                    "Your attempt was submitted automatically: " + e.reason(), "PROCTORING", "/student/tests", true);
        });
    }

    /** Every minute: submit attempts whose time is over. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void autoSubmitExpired() {
        for (Attempt a : attempts.findByStatusAndDeadlineAtBefore(Attempt.Status.IN_PROGRESS, LocalDateTime.now().minusSeconds(30))) {
            finish(a, Attempt.Status.AUTO_SUBMITTED, "Time is over");
        }
    }

    // ---------------------------------------------------------------- results

    public List<AttemptResult> resultsForTest(Long testId) {
        testService.owned(testId);
        return attempts.findByTestIdOrderByPercentDesc(testId).stream().map(a -> result(a, false)).toList();
    }

    public AttemptResult resultFor(Long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt not found"));
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.STUDENT) {
            if (!a.getStudentUserId().equals(me.userId())) {
                throw ApiException.forbidden("Not your attempt");
            }
            return result(a, true);
        }
        testService.owned(a.getTestId());
        return result(a, false);
    }

    public List<AttemptResult> mineResults() {
        return attempts.findByStudentUserIdOrderByStartedAtDesc(CurrentUser.id()).stream()
                .filter(a -> a.getStatus().isFinished())
                .map(a -> result(a, true)).toList();
    }

    AttemptResult result(Attempt a, boolean forStudent) {
        Test t = testService.get(a.getTestId());
        Map<String, Object> breakdown = new LinkedHashMap<>();
        List<AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByOrderIndexAsc(a.getId());
        breakdown.put("mcqAnswered", qs.stream().filter(q -> q.getKind() == TestPoolItem.Kind.MCQ && q.getSelectedIndex() != null).count());
        breakdown.put("mcqCorrect", qs.stream().filter(q -> Boolean.TRUE.equals(q.getCorrect())).count());
        breakdown.put("mcqTotal", qs.stream().filter(q -> q.getKind() == TestPoolItem.Kind.MCQ).count());
        breakdown.put("coding", qs.stream().filter(q -> q.getKind() == TestPoolItem.Kind.CODING)
                .map(q -> Map.of("questionId", q.getRefId(),
                        "passed", q.getCodingPassed() == null ? 0 : q.getCodingPassed(),
                        "total", q.getCodingTotal() == null ? 0 : q.getCodingTotal(),
                        "marks", q.getMarks() == null ? BigDecimal.ZERO : q.getMarks(),
                        "maxMarks", q.getMaxMarks()))
                .toList());
        boolean hideTrust = forStudent;
        return new AttemptResult(a.getId(), t.getId(), t.getTitle(), a.getStudentUserId(), a.getStudentName(),
                a.getStudentLogin(), a.getStatus(), a.getMcqScore(), a.getCodingScore(), a.getTotalScore(), a.getMaxScore(),
                a.getPercent(), forStudent && t.getRoundId() != null ? null : a.getPassed(),
                hideTrust ? null : a.getTrustScore(), !hideTrust && a.isProctoringFlagged(), a.getTerminationReason(),
                a.getStartedAt(), a.getSubmittedAt(), breakdown);
    }

    // ---------------------------------------------------------------- internal checks

    /** Used by submission-service before accepting a coding submission inside a test. */
    public Map<String, Object> canSubmit(Long attemptId, String userId, String questionId) {
        Attempt a = attempts.findById(attemptId).orElse(null);
        if (a == null || !a.getStudentUserId().equals(userId)) {
            return Map.of("allowed", false, "reason", "Attempt not found");
        }
        if (a.getStatus().isFinished() || a.getDeadlineAt().isBefore(LocalDateTime.now())) {
            return Map.of("allowed", false, "reason", "The test is over");
        }
        if (attemptQuestions.findFirstByAttemptIdAndRefId(attemptId, questionId).isEmpty()) {
            return Map.of("allowed", false, "reason", "This problem is not part of your test");
        }
        Test t = testService.get(a.getTestId());
        return Map.of("allowed", true, "reason", "", "testId", t.getId(), "testOwnerUserId", t.getOwnerUserId());
    }

    public Map<String, Object> proctoringSettings(Long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt not found"));
        Test t = testService.get(a.getTestId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attemptId", a.getId());
        m.put("testId", t.getId());
        m.put("testOwnerUserId", t.getOwnerUserId());
        m.put("studentUserId", a.getStudentUserId());
        m.put("status", a.getStatus().name());
        m.put("deadlineAt", a.getDeadlineAt().toString());
        m.put("proctored", t.isProctored());
        m.put("cameraRequired", t.isCameraRequired());
        m.put("maxViolations", t.getMaxViolations());
        m.put("autoSubmit", t.isAutoSubmitOnViolations());
        m.put("checks", Texts.toList(t.getProctoringChecks()));
        return m;
    }

    // ---------------------------------------------------------------- helpers

    private Attempt mine(Long attemptId) {
        Attempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt not found"));
        if (!a.getStudentUserId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("Not your attempt");
        }
        return a;
    }

    private void ensureRunning(Attempt a) {
        if (a.getStatus().isFinished()) {
            throw ApiException.badRequest("This attempt is already submitted");
        }
        if (a.getDeadlineAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Time is over");
        }
    }

    private AttemptView view(Attempt a, Test t) {
        List<AttemptQuestion> qs = attemptQuestions.findByAttemptIdOrderByOrderIndexAsc(a.getId());
        Map<Long, McqQuestion> mcqMap = mcqs.findByIdIn(qs.stream().filter(q -> q.getKind() == TestPoolItem.Kind.MCQ)
                        .map(q -> Long.valueOf(q.getRefId())).toList())
                .stream().collect(Collectors.toMap(McqQuestion::getId, Function.identity()));
        List<AttemptQuestionView> views = new ArrayList<>();
        for (AttemptQuestion q : qs) {
            if (q.getKind() == TestPoolItem.Kind.MCQ) {
                McqQuestion m = mcqMap.get(Long.valueOf(q.getRefId()));
                if (m == null) {
                    continue;
                }
                List<String> original = mcqService.options(m);
                List<Integer> order = optionOrder(q);
                List<String> shown = order.stream().map(original::get).toList();
                Integer selectedDisplay = q.getSelectedIndex() == null ? null : order.indexOf(q.getSelectedIndex());
                views.add(new AttemptQuestionView(q.getId(), q.getKind(), q.getOrderIndex(), m.getText(), shown,
                        selectedDisplay, null, null, null, null, null, q.getMaxMarks()));
            } else {
                AssessmentClients.CodingQuestion cq = null;
                try {
                    cq = clients.codingQuestion(q.getRefId());
                } catch (Exception ex) {
                    log.warn("Coding question {} unavailable", q.getRefId());
                }
                views.add(new AttemptQuestionView(q.getId(), q.getKind(), q.getOrderIndex(), null, null, null,
                        q.getRefId(), cq == null ? "Coding problem" : cq.title(), cq == null ? null : cq.difficulty(),
                        q.getCodingPassed(), q.getCodingTotal(), q.getMaxMarks()));
            }
        }
        long remaining = Math.max(0, Duration.between(LocalDateTime.now(), a.getDeadlineAt()).getSeconds());
        ProctoringSettings settings = new ProctoringSettings(t.isProctored(), t.isCameraRequired(), t.getMaxViolations(),
                t.isAutoSubmitOnViolations(), Texts.toList(t.getProctoringChecks()));
        return new AttemptView(a.getId(), t.getId(), t.getTitle(), t.getInstructions(), a.getStatus(), a.getStartedAt(),
                a.getDeadlineAt(), a.getStatus().isFinished() ? 0 : remaining, settings, views);
    }

    private List<Integer> optionOrder(AttemptQuestion q) {
        try {
            return mapper.readValue(q.getOptionOrder(), new TypeReference<List<Integer>>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return List.of(0, 1, 2, 3);
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
