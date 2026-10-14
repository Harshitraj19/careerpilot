package com.placify.proctoring.service;

import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.proctoring.config.ProctoringProperties;
import com.placify.proctoring.domain.ProctoringSession;
import com.placify.proctoring.domain.Violation;
import com.placify.proctoring.domain.ViolationType;
import com.placify.proctoring.repository.ProctoringSessionRepository;
import com.placify.proctoring.repository.ViolationRepository;
import com.placify.proctoring.vision.SessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records violations, keeps the trust score up to date, warns the student live
 * and ends the attempt when the limit is reached.
 *
 * <pre>
 * trust score = 100 - 15 x HIGH - 7 x MEDIUM - 2 x LOW   (never below 0)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ProctoringSessionRepository sessions;
    private final ViolationRepository violations;
    private final EventPublisher publisher;
    private final ProctoringProperties props;

    /** In-memory state of active sessions (single-instance service). */
    private final Map<Long, SessionState> states = new ConcurrentHashMap<>();

    public SessionState state(Long sessionId) {
        return states.computeIfAbsent(sessionId, id -> new SessionState());
    }

    public void forget(Long sessionId) {
        SessionState s = states.remove(sessionId);
        if (s != null) {
            s.release();
        }
    }

    /**
     * @return true if the violation was recorded (it may be skipped when the check
     *         is disabled for this test or the same type happened a few seconds ago)
     */
    @Transactional
    public boolean record(Long sessionId, ViolationType type, String detail, Violation.Source source, String snapshotPath) {
        ProctoringSession s = sessions.lockById(sessionId).orElse(null);
        if (s == null || s.getStatus() != ProctoringSession.Status.ACTIVE || !s.checkEnabled(type)) {
            return false;
        }
        SessionState state = state(sessionId);
        LocalDateTime now = LocalDateTime.now();
        synchronized (state) {
            LocalDateTime last = state.lastRecorded.get(type);
            if (last != null && last.plusSeconds(props.debounceSeconds()).isAfter(now)) {
                return false;
            }
            state.lastRecorded.put(type, now);
        }

        violations.save(Violation.builder().sessionId(sessionId).type(type).severity(type.severity()).source(source)
                .detail(detail).snapshotPath(snapshotPath).occurredAt(now).build());
        switch (type.severity()) {
            case HIGH -> s.setHighCount(s.getHighCount() + 1);
            case MEDIUM -> s.setMediumCount(s.getMediumCount() + 1);
            case LOW -> s.setLowCount(s.getLowCount() + 1);
        }
        if (type.countsTowardLimit()) {
            s.setViolationCount(s.getViolationCount() + 1);
        }
        s.setTrustScore(trustScore(s.getHighCount(), s.getMediumCount(), s.getLowCount()));

        publisher.publish(RoutingKeys.PROCTORING_WARNING, new Events.ProctoringWarningEvent(s.getStudentUserId(), s.getId(),
                s.getAttemptId(), type.name(), type.message(), s.getViolationCount(), s.getMaxViolations()));

        if (s.isAutoSubmit() && s.getViolationCount() >= s.getMaxViolations()) {
            s.setStatus(ProctoringSession.Status.TERMINATED);
            s.setEndedAt(now);
            String reason = "Too many proctoring violations (" + s.getViolationCount() + "/" + s.getMaxViolations() + ")";
            publisher.publish(RoutingKeys.PROCTORING_TERMINATE, new Events.ProctoringTerminateEvent(s.getAttemptId(), s.getId(), reason));
            log.info("Proctoring session {} terminated: {}", s.getId(), reason);
            forget(sessionId);
        }
        return true;
    }

    public static int trustScore(int high, int medium, int low) {
        return Math.max(0, 100 - 15 * high - 7 * medium - 2 * low);
    }

    public boolean isFlagged(ProctoringSession s) {
        return s.getStatus() == ProctoringSession.Status.TERMINATED
                || s.getTrustScore() < props.flagBelowTrustScore()
                || Boolean.FALSE.equals(s.getIdentityVerified());
    }

    public String flagReason(ProctoringSession s) {
        if (s.getStatus() == ProctoringSession.Status.TERMINATED) {
            return "Attempt was ended automatically after too many violations";
        }
        if (Boolean.FALSE.equals(s.getIdentityVerified())) {
            return "Face did not match the registered photo";
        }
        if (s.getTrustScore() < props.flagBelowTrustScore()) {
            return "Trust score below " + props.flagBelowTrustScore();
        }
        return null;
    }
}
