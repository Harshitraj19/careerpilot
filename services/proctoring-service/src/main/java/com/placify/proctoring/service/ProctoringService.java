package com.placify.proctoring.service;

import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.UserPrincipal;
import com.placify.common.storage.FileStorageService;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import com.placify.proctoring.config.ProctoringProperties;
import com.placify.proctoring.domain.ProctoringSession;
import com.placify.proctoring.domain.Violation;
import com.placify.proctoring.domain.ViolationType;
import com.placify.proctoring.repository.ProctoringSessionRepository;
import com.placify.proctoring.repository.ViolationRepository;
import com.placify.proctoring.vision.FaceMatcher;
import com.placify.proctoring.vision.OpenCvSupport;
import com.placify.proctoring.vision.PhoneDetector;
import com.placify.proctoring.web.ProctoringDtos.BrowserEvent;
import com.placify.proctoring.web.ProctoringDtos.Report;
import com.placify.proctoring.web.ProctoringDtos.SessionStatus;
import com.placify.proctoring.web.ProctoringDtos.StartRequest;
import com.placify.proctoring.web.ProctoringDtos.Summary;
import com.placify.proctoring.web.ProctoringDtos.ViolationView;
import com.placify.proctoring.web.ProctoringDtos.WarningView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Proctoring sessions: consent + identity check at the start, frame intake
 * (analysed asynchronously from a RabbitMQ queue), browser events and reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProctoringService {

    private final ProctoringSessionRepository sessions;
    private final ViolationRepository violations;
    private final ViolationService violationService;
    private final FileStorageService storage;
    private final InternalClient client;
    private final FaceMatcher faceMatcher;
    private final OpenCvSupport cv;
    private final PhoneDetector phoneDetector;
    private final EventPublisher publisher;
    private final ProctoringProperties props;

    // ---------------------------------------------------------------- start

    @Transactional
    public SessionStatus start(StartRequest req) {
        UserPrincipal me = CurrentUser.get();
        Map<String, Object> settings = client.get(ServiceNames.ASSESSMENT,
                "/internal/assessment/attempts/" + req.attemptId() + "/proctoring-settings",
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (!me.userId().equals(settings.get("studentUserId"))) {
            throw ApiException.forbidden("Not your attempt");
        }
        if (!"IN_PROGRESS".equals(settings.get("status"))) {
            throw ApiException.badRequest("This attempt is not in progress");
        }
        ProctoringSession existing = sessions.findByAttemptId(req.attemptId()).orElse(null);
        if (existing != null) {
            return status(existing);
        }
        boolean cameraRequired = Boolean.TRUE.equals(settings.get("cameraRequired"));
        byte[] reference = decodeImage(req.referencePhoto());
        if (cameraRequired && reference == null) {
            throw ApiException.badRequest("A camera photo is required to start this test");
        }

        @SuppressWarnings("unchecked")
        List<String> checks = (List<String>) settings.getOrDefault("checks", List.of());
        ProctoringSession s = ProctoringSession.builder()
                .attemptId(req.attemptId())
                .testId(((Number) settings.get("testId")).longValue())
                .testOwnerUserId((String) settings.get("testOwnerUserId"))
                .studentUserId(me.userId())
                .trustScore(100)
                .maxViolations(((Number) settings.getOrDefault("maxViolations", 5)).intValue())
                .autoSubmit(Boolean.TRUE.equals(settings.get("autoSubmit")))
                .cameraRequired(cameraRequired)
                .enabledChecks(String.join(",", checks))
                .status(ProctoringSession.Status.ACTIVE)
                .consentAt(LocalDateTime.now())
                .build();

        if (reference != null) {
            s.setReferencePhotoPath(storage.save("proctoring", reference, "jpg"));
            verifyIdentity(s, reference, me.userId());
        }
        s = sessions.save(s);
        if (reference != null) {
            violationService.state(s.getId()).referencePhoto = reference;
        }
        if (Boolean.FALSE.equals(s.getIdentityVerified())) {
            violationService.record(s.getId(), ViolationType.DIFFERENT_PERSON,
                    String.format("Start photo similarity %.2f", s.getIdentitySimilarity()), Violation.Source.CAMERA,
                    s.getReferencePhotoPath());
        }
        return status(s);
    }

    /** Compares the start photo with the profile photo uploaded by the student. */
    private void verifyIdentity(ProctoringSession s, byte[] reference, String userId) {
        try {
            byte[] profilePhoto = client.getBytes(ServiceNames.PROFILE, "/internal/profile/students/" + userId + "/photo");
            double sim = faceMatcher.similarity(reference, profilePhoto);
            if (sim < 0) {
                s.setIdentityVerified(null); // no face found in one of the photos
            } else {
                s.setIdentitySimilarity(sim);
                s.setIdentityVerified(sim >= props.identityThreshold());
            }
        } catch (Exception ex) {
            log.info("Identity check skipped for {}: {}", userId, ex.getMessage());
            s.setIdentityVerified(null);
        }
    }

    // ---------------------------------------------------------------- frames and events

    /** Stores the frame and queues it for analysis; returns the live status for the exam page. */
    public SessionStatus submitFrame(Long sessionId, String image) {
        ProctoringSession s = mine(sessionId);
        if (s.getStatus() == ProctoringSession.Status.ACTIVE) {
            byte[] bytes = decodeImage(image);
            if (bytes == null) {
                throw ApiException.badRequest("Invalid image");
            }
            String path = storage.save("frames", bytes, "jpg");
            publisher.publish(RoutingKeys.PROCTORING_FRAME, new Events.FrameAnalysisRequest(sessionId, path, LocalDateTime.now()));
        }
        return status(s);
    }

    public SessionStatus browserEvent(Long sessionId, BrowserEvent event) {
        ProctoringSession s = mine(sessionId);
        if (!event.type().isBrowserEvent()) {
            throw ApiException.badRequest("Only browser events can be reported here");
        }
        violationService.record(sessionId, event.type(), Texts.truncate(event.detail(), 300), Violation.Source.BROWSER, null);
        return status(sessions.findById(s.getId()).orElse(s));
    }

    @Transactional
    public SessionStatus end(Long sessionId) {
        ProctoringSession s = mine(sessionId);
        if (s.getStatus() == ProctoringSession.Status.ACTIVE) {
            s.setStatus(ProctoringSession.Status.ENDED);
            s.setEndedAt(LocalDateTime.now());
        }
        violationService.forget(sessionId);
        return status(s);
    }

    public SessionStatus currentStatus(Long sessionId) {
        return status(mine(sessionId));
    }

    private ProctoringSession mine(Long sessionId) {
        ProctoringSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session not found"));
        if (!s.getStudentUserId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("Not your session");
        }
        return s;
    }

    private SessionStatus status(ProctoringSession s) {
        List<WarningView> recent = violations.findTop5BySessionIdOrderByOccurredAtDesc(s.getId()).stream()
                .map(WarningView::of).toList();
        return new SessionStatus(s.getId(), s.getAttemptId(), s.getStatus(), s.getViolationCount(), s.getMaxViolations(),
                s.isCameraRequired(), Texts.toList(s.getEnabledChecks()), s.getIdentityVerified(), recent, cv.available(),
                phoneDetector.available());
    }

    // ---------------------------------------------------------------- reports

    /** Full evidence report for the test owner (company/TPO). */
    public Report report(Long attemptId) {
        ProctoringSession s = sessions.findByAttemptId(attemptId)
                .orElseThrow(() -> ApiException.notFound("This attempt was not proctored"));
        UserPrincipal me = CurrentUser.get();
        if (!me.isStaff() && !me.userId().equals(s.getTestOwnerUserId())) {
            throw ApiException.forbidden("Only the test owner can see this report");
        }
        List<ViolationView> list = violations.findBySessionIdOrderByOccurredAtAsc(s.getId()).stream()
                .map(ViolationView::of).toList();
        return new Report(s.getId(), s.getAttemptId(), s.getTestId(), s.getStudentUserId(), s.getStatus(), s.getTrustScore(),
                violationService.isFlagged(s), violationService.flagReason(s), s.getIdentityVerified(),
                s.getIdentitySimilarity(), faceMatcher.name(), s.getFramesAnalysed(), s.getHighCount(), s.getMediumCount(),
                s.getLowCount(), s.getStartedAt(), s.getEndedAt(), list);
    }

    public byte[] snapshot(Long violationId) {
        Violation v = violations.findById(violationId).orElseThrow(() -> ApiException.notFound("Violation not found"));
        ProctoringSession s = sessions.findById(v.getSessionId()).orElseThrow();
        UserPrincipal me = CurrentUser.get();
        if (!me.isStaff() && !me.userId().equals(s.getTestOwnerUserId())) {
            throw ApiException.forbidden("Only the test owner can see evidence");
        }
        if (v.getSnapshotPath() == null) {
            throw ApiException.notFound("No snapshot for this violation");
        }
        return storage.read(v.getSnapshotPath());
    }

    public Summary summary(Long attemptId) {
        ProctoringSession s = sessions.findByAttemptId(attemptId).orElseThrow(() -> ApiException.notFound("Not proctored"));
        return new Summary(attemptId, s.getTrustScore(), violationService.isFlagged(s), s.getViolationCount(),
                s.getStatus() == ProctoringSession.Status.TERMINATED, !Boolean.FALSE.equals(s.getIdentityVerified()));
    }

    // ---------------------------------------------------------------- helpers

    static byte[] decodeImage(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        String base64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            return bytes.length > 2 * 1024 * 1024 ? null : bytes;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
