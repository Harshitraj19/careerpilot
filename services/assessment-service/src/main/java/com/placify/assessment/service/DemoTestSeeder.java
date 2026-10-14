package com.placify.assessment.service;

import com.placify.assessment.client.AssessmentClients;
import com.placify.assessment.client.AssessmentClients.CodingSummary;
import com.placify.assessment.client.AssessmentClients.OpenTestRound;
import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.domain.Test;
import com.placify.assessment.domain.TestPoolItem;
import com.placify.assessment.repository.McqQuestionRepository;
import com.placify.assessment.repository.TestPoolItemRepository;
import com.placify.assessment.repository.TestRepository;
import com.placify.common.demo.DemoData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Demo online tests (DEMO_DATA=true): one proctored test for the first-round online test of every
 * demo drive, linked to that round so applicants find it on their Tests page, plus a campus-wide
 * proctored mock test. Tests need the placement and coding services, so this runs on a schedule
 * after start-up and stops once every open test round has a test.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoTestSeeder {

    static final String MOCK_TEST_TITLE = "Campus Readiness Mock Test (proctored)";
    private static final List<String> CHECKS = List.of("NO_FACE", "MULTIPLE_FACES", "DIFFERENT_PERSON", "LOOKING_AWAY",
            "CAMERA_BLOCKED", "PHONE_DETECTED", "TAB_SWITCH", "FULLSCREEN_EXIT", "COPY_PASTE", "SCREEN_SHARE_STOPPED",
            "MULTIPLE_MONITORS");

    private final TestRepository tests;
    private final TestPoolItemRepository pool;
    private final McqQuestionRepository mcqs;
    private final AssessmentClients clients;
    private final TransactionTemplate tx;

    private volatile boolean done;

    @Scheduled(initialDelay = 25_000, fixedDelay = 30_000)
    public void seed() {
        if (done) {
            return;
        }
        try {
            List<CodingSummary> testProblems = clients.codingQuestionsBy(DemoData.TPO_ID).stream()
                    .filter(q -> "TEST_ONLY".equals(q.visibility())).toList();
            List<OpenTestRound> rounds = clients.openTestRounds();
            int created = 0;
            for (OpenTestRound r : rounds) {
                if (DemoData.COMPANIES.stream().anyMatch(c -> c.id().equals(r.companyUserId())) && !tests.existsByRoundId(r.roundId())) {
                    Long testId = tx.execute(status -> createDriveTest(r, testProblems));
                    clients.linkRound(r.roundId(), testId);
                    created++;
                }
            }
            if (!tests.existsByTitle(MOCK_TEST_TITLE)) {
                tx.executeWithoutResult(status -> createMockTest(testProblems));
                created++;
            }
            if (created > 0) {
                log.info("Created {} demo online tests", created);
            }
            done = true;
        } catch (Exception ex) {
            log.debug("Demo tests not created yet ({}); retrying", ex.getMessage());
        }
    }

    private Long createDriveTest(OpenTestRound r, List<CodingSummary> problems) {
        boolean coding = "CODING_TEST".equals(r.type())
                || (r.description() != null && r.description().toLowerCase(Locale.ROOT).contains("coding"));
        LocalDateTime end = r.scheduledAt() == null ? LocalDateTime.now().plusDays(14) : r.scheduledAt().toLocalDate().atTime(LocalTime.of(23, 59));
        List<Long> mcqIds = coding
                ? pick(McqQuestion.Topic.TECHNICAL, 10).stream().map(McqQuestion::getId).toList()
                : mixedPool(5);
        Test t = tests.save(Test.builder()
                .title(r.companyName() + " - " + r.name())
                .instructions("Online test for " + r.jobTitle() + " at " + r.companyName() + ".\n"
                        + (r.description() == null ? "" : r.description() + "\n")
                        + "Keep your camera on, share your entire screen and stay in full screen until you submit. "
                        + "Switching tabs, copy-paste, other people or a phone in view are recorded as violations.")
                .type(coding ? Test.Type.MIXED : Test.Type.APTITUDE)
                .status(Test.Status.PUBLISHED)
                .ownerUserId(r.companyUserId()).ownerName(r.companyName())
                .jobId(r.jobId()).roundId(r.roundId())
                .durationMinutes(coding ? 45 : 30)
                .windowStart(LocalDateTime.now().minusHours(1)).windowEnd(end)
                .cutoffPercent(r.cutoffPercent() == null ? new BigDecimal("50") : r.cutoffPercent())
                .mcqCount(coding ? 6 : 12).marksPerMcq(1)
                .codingCount(coding && !problems.isEmpty() ? 1 : 0)
                .randomize(true)
                .proctored(true).cameraRequired(true).maxViolations(6).autoSubmitOnViolations(true)
                .proctoringChecks(String.join(",", CHECKS))
                .build());
        savePool(t.getId(), mcqIds, coding ? problems : List.of(), 10);
        return t.getId();
    }

    private void createMockTest(List<CodingSummary> problems) {
        Test t = tests.save(Test.builder()
                .title(MOCK_TEST_TITLE)
                .instructions("A practice run of a real campus test, open to every student: 10 aptitude and technical MCQs "
                        + "and one coding problem in 40 minutes, with full proctoring (camera, screen sharing, full screen). "
                        + "Your score does not affect any application.")
                .type(problems.isEmpty() ? Test.Type.APTITUDE : Test.Type.MIXED)
                .status(Test.Status.PUBLISHED)
                .ownerUserId(DemoData.TPO_ID).ownerName("Placement office")
                .durationMinutes(40)
                .windowStart(LocalDateTime.now().minusHours(1)).windowEnd(LocalDateTime.now().plusDays(30))
                .cutoffPercent(new BigDecimal("50"))
                .mcqCount(10).marksPerMcq(1).codingCount(problems.isEmpty() ? 0 : 1)
                .randomize(true)
                .proctored(true).cameraRequired(true).maxViolations(6).autoSubmitOnViolations(false)
                .proctoringChecks(String.join(",", CHECKS))
                .build());
        savePool(t.getId(), mixedPool(5), problems, 10);
    }

    /** About the same number of questions from each topic, mixed difficulty. */
    private List<Long> mixedPool(int perTopic) {
        List<Long> ids = new ArrayList<>();
        for (McqQuestion.Topic topic : McqQuestion.Topic.values()) {
            pick(topic, perTopic).forEach(q -> ids.add(q.getId()));
        }
        return ids;
    }

    private List<McqQuestion> pick(McqQuestion.Topic topic, int count) {
        return mcqs.randomApproved(topic.name(), null, count);
    }

    private void savePool(Long testId, List<Long> mcqIds, List<CodingSummary> problems, int codingPoints) {
        for (Long id : mcqIds) {
            pool.save(TestPoolItem.builder().testId(testId).kind(TestPoolItem.Kind.MCQ).refId(String.valueOf(id)).points(0).build());
        }
        List<CodingSummary> shuffled = new ArrayList<>(problems);
        Collections.shuffle(shuffled);
        Map<String, CodingSummary> unique = shuffled.stream().limit(2)
                .collect(Collectors.toMap(CodingSummary::id, q -> q, (a, b) -> a));
        for (CodingSummary q : unique.values()) {
            pool.save(TestPoolItem.builder().testId(testId).kind(TestPoolItem.Kind.CODING).refId(q.id()).points(codingPoints).build());
        }
    }
}
