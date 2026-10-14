package com.placify.assessment.service;

import com.placify.assessment.client.AssessmentClients;
import com.placify.assessment.domain.Test;
import com.placify.assessment.repository.TestRepository;
import com.placify.common.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Test reminders (one hour before opening) and closing finished tests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestScheduler {

    private final TestRepository tests;
    private final AssessmentClients clients;
    private final EventPublisher publisher;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void reminders() {
        LocalDateTime now = LocalDateTime.now();
        for (Test t : tests.findByStatusAndReminderSentFalseAndWindowStartBetween(Test.Status.PUBLISHED, now, now.plusHours(1))) {
            t.setReminderSent(true);
            List<String> audience;
            try {
                audience = t.getRoundId() != null ? clients.roundCandidates(t.getRoundId()) : List.of("ROLE:STUDENT");
            } catch (Exception ex) {
                continue;
            }
            publisher.notifyUsers(audience, "Test starts soon: " + t.getTitle(),
                    "Opens at " + t.getWindowStart().toLocalTime() + ". Use a laptop with a working camera"
                            + (t.isProctored() ? " (this test is proctored)." : "."), "REMINDER", "/student/tests",
                    t.getRoundId() != null);
        }
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    @Transactional
    public void closeFinished() {
        for (Test t : tests.findByStatusAndWindowEndBefore(Test.Status.PUBLISHED, LocalDateTime.now().minusMinutes(5))) {
            t.setStatus(Test.Status.CLOSED);
            log.info("Closed test {} ({})", t.getId(), t.getTitle());
        }
    }
}
