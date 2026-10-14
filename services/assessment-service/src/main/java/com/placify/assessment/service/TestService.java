package com.placify.assessment.service;

import com.placify.assessment.client.AssessmentClients;
import com.placify.assessment.domain.Attempt;
import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.domain.Test;
import com.placify.assessment.domain.TestPoolItem;
import com.placify.assessment.repository.AttemptRepository;
import com.placify.assessment.repository.McqQuestionRepository;
import com.placify.assessment.repository.TestPoolItemRepository;
import com.placify.assessment.repository.TestRepository;
import com.placify.assessment.web.AssessmentDtos.PoolCoding;
import com.placify.assessment.web.AssessmentDtos.PoolItemView;
import com.placify.assessment.web.AssessmentDtos.TestRequest;
import com.placify.assessment.web.AssessmentDtos.TestView;
import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Test authoring for companies and the TPO: question pools, timing window,
 * cut-off, randomisation and proctoring settings, link to a job round.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestService {

    public static final Set<String> ALL_CHECKS = Set.of(
            "NO_FACE", "MULTIPLE_FACES", "DIFFERENT_PERSON", "LOOKING_AWAY", "EXCESSIVE_MOVEMENT", "PHONE_DETECTED",
            "CAMERA_BLOCKED", "TAB_SWITCH", "FULLSCREEN_EXIT", "COPY_PASTE", "NOISE", "SCREEN_SHARE_STOPPED", "MULTIPLE_MONITORS");

    private final TestRepository tests;
    private final TestPoolItemRepository pool;
    private final AttemptRepository attempts;
    private final McqQuestionRepository mcqs;
    private final AssessmentClients clients;
    private final EventPublisher publisher;

    @Value("${placify.assessment.default-cutoff-percent:50}")
    private BigDecimal defaultCutoff;

    public Test get(Long id) {
        return tests.findById(id).orElseThrow(() -> ApiException.notFound("Test not found"));
    }

    public Test owned(Long id) {
        Test t = get(id);
        UserPrincipal me = CurrentUser.get();
        if (!me.isStaff() && !me.userId().equals(t.getOwnerUserId())) {
            throw ApiException.forbidden("Not your test");
        }
        return t;
    }

    @Transactional
    public Test create(TestRequest req) {
        validate(req);
        UserPrincipal me = CurrentUser.get();
        Long jobId = null;
        if (req.roundId() != null) {
            AssessmentClients.RoundInfo round = clients.round(req.roundId());
            if (me.role() == Role.COMPANY && !me.userId().equals(round.companyUserId())) {
                throw ApiException.forbidden("This round belongs to another company");
            }
            if (!"APTITUDE_TEST".equals(round.type()) && !"CODING_TEST".equals(round.type())) {
                throw ApiException.badRequest("Tests can only be linked to aptitude or coding test rounds");
            }
            jobId = round.jobId();
        }
        Test t = Test.builder()
                .ownerUserId(me.userId())
                .ownerName(me.name())
                .status(Test.Status.DRAFT)
                .roundId(req.roundId())
                .jobId(jobId)
                .build();
        apply(t, req);
        t = tests.save(t);
        savePool(t.getId(), req);
        if (req.roundId() != null) {
            try {
                clients.linkRound(req.roundId(), t.getId());
            } catch (Exception ex) {
                log.warn("Could not link test {} to round {}: {}", t.getId(), req.roundId(), ex.getMessage());
            }
        }
        return t;
    }

    @Transactional
    public Test update(Long id, TestRequest req) {
        validate(req);
        Test t = owned(id);
        if (t.getStatus() != Test.Status.DRAFT && attempts.countByTestId(id) > 0) {
            throw ApiException.badRequest("Students have already started this test; it cannot be changed");
        }
        apply(t, req);
        pool.deleteByTestId(id);
        pool.flush();
        savePool(id, req);
        return t;
    }

    private void apply(Test t, TestRequest req) {
        t.setTitle(req.title().trim());
        t.setInstructions(req.instructions());
        t.setType(req.type());
        t.setDurationMinutes(req.durationMinutes());
        t.setWindowStart(req.windowStart());
        t.setWindowEnd(req.windowEnd());
        t.setCutoffPercent(req.cutoffPercent() == null ? defaultCutoff : req.cutoffPercent());
        t.setMcqCount(req.mcqCount());
        t.setMarksPerMcq(req.marksPerMcq() == null ? 1 : req.marksPerMcq());
        t.setCodingCount(req.codingCount());
        t.setRandomize(req.randomize());
        t.setProctored(req.proctored());
        t.setCameraRequired(req.proctored() && req.cameraRequired());
        t.setMaxViolations(req.maxViolations() == null ? 5 : req.maxViolations());
        t.setAutoSubmitOnViolations(req.autoSubmitOnViolations());
        Set<String> checks = new LinkedHashSet<>();
        if (req.proctoringChecks() != null) {
            req.proctoringChecks().stream().map(String::toUpperCase).filter(ALL_CHECKS::contains).forEach(checks::add);
        }
        if (req.proctored() && checks.isEmpty()) {
            checks.addAll(List.of("NO_FACE", "MULTIPLE_FACES", "TAB_SWITCH", "FULLSCREEN_EXIT", "COPY_PASTE"));
        }
        t.setProctoringChecks(String.join(",", checks));
    }

    private void validate(TestRequest req) {
        if (!req.windowEnd().isAfter(req.windowStart())) {
            throw ApiException.badRequest("The window end must be after the start");
        }
        int mcqPool = req.mcqIds() == null ? 0 : new LinkedHashSet<>(req.mcqIds()).size();
        int codingPool = req.codingQuestions() == null ? 0 : req.codingQuestions().size();
        if (req.mcqCount() > mcqPool) {
            throw ApiException.badRequest("MCQ count (" + req.mcqCount() + ") is larger than the MCQ pool (" + mcqPool + ")");
        }
        if (req.codingCount() > codingPool) {
            throw ApiException.badRequest("Coding count is larger than the coding pool");
        }
        if (req.mcqCount() + req.codingCount() == 0) {
            throw ApiException.badRequest("A test needs at least one question");
        }
        if (req.mcqIds() != null && !req.mcqIds().isEmpty()) {
            List<McqQuestion> found = mcqs.findByIdIn(req.mcqIds());
            if (found.size() != mcqPool) {
                throw ApiException.badRequest("Some MCQs do not exist");
            }
            if (found.stream().anyMatch(m -> !m.isApproved())) {
                throw ApiException.badRequest("All MCQs in a test must be approved");
            }
        }
    }

    private void savePool(Long testId, TestRequest req) {
        if (req.mcqIds() != null) {
            for (Long id : new LinkedHashSet<>(req.mcqIds())) {
                pool.save(TestPoolItem.builder().testId(testId).kind(TestPoolItem.Kind.MCQ).refId(String.valueOf(id)).points(0).build());
            }
        }
        if (req.codingQuestions() != null) {
            for (PoolCoding c : req.codingQuestions()) {
                clients.codingQuestion(c.questionId()); // validates it exists
                pool.save(TestPoolItem.builder().testId(testId).kind(TestPoolItem.Kind.CODING).refId(c.questionId())
                        .points(c.points()).build());
            }
        }
    }

    @Audited(action = "TEST_PUBLISHED", target = "#id")
    @Transactional
    public Test publish(Long id) {
        Test t = owned(id);
        if (t.getStatus() != Test.Status.DRAFT) {
            throw ApiException.badRequest("Only draft tests can be published");
        }
        if (!t.getWindowEnd().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("The test window has already ended");
        }
        t.setStatus(Test.Status.PUBLISHED);
        List<String> audience = t.getRoundId() != null ? clients.roundCandidates(t.getRoundId()) : List.of("ROLE:STUDENT");
        publisher.notifyUsers(audience, "Test scheduled: " + t.getTitle(),
                "Opens " + t.getWindowStart().toLocalDate() + " at " + t.getWindowStart().toLocalTime() + " for "
                        + t.getDurationMinutes() + " minutes" + (t.isProctored() ? " (camera proctored)" : "") + ".",
                "TEST", "/student/tests", t.getRoundId() != null);
        return t;
    }

    @Transactional
    public Test close(Long id) {
        Test t = owned(id);
        t.setStatus(Test.Status.CLOSED);
        return t;
    }

    @Transactional
    public void delete(Long id) {
        Test t = owned(id);
        if (attempts.countByTestId(id) > 0) {
            throw ApiException.badRequest("This test has attempts and cannot be deleted; close it instead");
        }
        tests.delete(t);
    }

    // ---------------------------------------------------------------- views

    public List<TestView> listForCurrentUser() {
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.STUDENT) {
            return available(me);
        }
        List<Test> list = me.isStaff() ? tests.findAllByOrderByCreatedAtDesc() : tests.findByOwnerUserIdOrderByCreatedAtDesc(me.userId());
        return list.stream().map(t -> view(t, null)).toList();
    }

    /** Published tests the student is allowed to take now or later. */
    private List<TestView> available(UserPrincipal me) {
        List<TestView> out = new ArrayList<>();
        for (Test t : tests.findByStatusAndWindowEndAfterOrderByWindowStartAsc(Test.Status.PUBLISHED, LocalDateTime.now())) {
            Optional<Attempt> mine = attempts.findByTestIdAndStudentUserId(t.getId(), me.userId());
            if (t.getRoundId() != null && mine.isEmpty() && !safeCandidate(t.getRoundId(), me.userId())) {
                continue;
            }
            out.add(view(t, mine.orElse(null)));
        }
        // finished attempts of closed/expired tests too
        for (Attempt a : attempts.findByStudentUserIdOrderByStartedAtDesc(me.userId())) {
            if (out.stream().noneMatch(v -> v.id().equals(a.getTestId()))) {
                tests.findById(a.getTestId()).ifPresent(t -> out.add(view(t, a)));
            }
        }
        return out;
    }

    private boolean safeCandidate(Long roundId, String userId) {
        try {
            return clients.isRoundCandidate(roundId, userId);
        } catch (Exception ex) {
            return false;
        }
    }

    public TestView view(Test t, Attempt mine) {
        UserPrincipal me = CurrentUser.get();
        boolean owner = me.isStaff() || me.userId().equals(t.getOwnerUserId());
        List<TestPoolItem> items = owner ? pool.findByTestId(t.getId()) : List.of();
        return new TestView(t.getId(), t.getTitle(), t.getInstructions(), t.getType(), t.getStatus(), t.getOwnerUserId(),
                t.getOwnerName(), t.getJobId(), t.getRoundId(), t.getDurationMinutes(), t.getWindowStart(), t.getWindowEnd(),
                t.getCutoffPercent(), t.getMcqCount(), t.getMarksPerMcq(), t.getCodingCount(), t.isRandomize(), t.isProctored(),
                t.isCameraRequired(), t.getMaxViolations(), t.isAutoSubmitOnViolations(), Texts.toList(t.getProctoringChecks()),
                owner ? (int) items.stream().filter(i -> i.getKind() == TestPoolItem.Kind.MCQ).count() : null,
                owner ? (int) items.stream().filter(i -> i.getKind() == TestPoolItem.Kind.CODING).count() : null,
                owner ? attempts.countByTestId(t.getId()) : null,
                mine == null ? null : mine.getStatus(), mine == null ? null : mine.getId());
    }

    public List<PoolItemView> poolView(Long testId) {
        owned(testId);
        List<TestPoolItem> items = pool.findByTestId(testId);
        Map<Long, McqQuestion> mcqMap = mcqs.findByIdIn(items.stream().filter(i -> i.getKind() == TestPoolItem.Kind.MCQ)
                        .map(i -> Long.valueOf(i.getRefId())).toList())
                .stream().collect(Collectors.toMap(McqQuestion::getId, Function.identity()));
        return items.stream().map(i -> {
            String title;
            if (i.getKind() == TestPoolItem.Kind.MCQ) {
                McqQuestion m = mcqMap.get(Long.valueOf(i.getRefId()));
                title = m == null ? "(deleted)" : Texts.truncate(m.getText(), 120);
            } else {
                title = Optional.ofNullable(safeCoding(i.getRefId())).map(AssessmentClients.CodingQuestion::title).orElse("(coding problem)");
            }
            return new PoolItemView(i.getId(), i.getKind(), i.getRefId(), i.getPoints(), title);
        }).toList();
    }

    private AssessmentClients.CodingQuestion safeCoding(String id) {
        try {
            return clients.codingQuestion(id);
        } catch (Exception ex) {
            return null;
        }
    }
}
