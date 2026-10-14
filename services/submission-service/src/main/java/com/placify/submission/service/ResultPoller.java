package com.placify.submission.service;

import com.placify.submission.client.Judge0Client;
import com.placify.submission.domain.SubmissionToken;
import com.placify.submission.repository.SubmissionTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Backup to Judge0 callbacks: polls unfinished tokens and expires stuck ones.
 * (Callbacks need Judge0 to reach this service; polling always works.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultPoller {

    private final SubmissionTokenRepository tokens;
    private final Judge0Client judge0;
    private final SubmissionService service;

    @Scheduled(fixedDelayString = "${placify.judge0.poll-interval-ms:3000}")
    public void poll() {
        List<SubmissionToken> pending = tokens.findByStatusInAndExpiresAtAfter(
                EnumSet.of(SubmissionToken.Status.pending, SubmissionToken.Status.processing), LocalDateTime.now());
        for (SubmissionToken t : pending) {
            try {
                Map<String, Object> body = judge0.fetch(t.getToken());
                if (body != null) {
                    service.complete(t, body);
                }
            } catch (Exception ex) {
                log.debug("Polling token {} failed: {}", t.getToken(), ex.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireStuck() {
        for (SubmissionToken t : tokens.findByStatusInAndExpiresAtBefore(
                EnumSet.of(SubmissionToken.Status.pending, SubmissionToken.Status.processing), LocalDateTime.now())) {
            try {
                service.expire(t);
            } catch (Exception ex) {
                log.warn("Could not expire token {}: {}", t.getToken(), ex.getMessage());
            }
        }
    }
}
