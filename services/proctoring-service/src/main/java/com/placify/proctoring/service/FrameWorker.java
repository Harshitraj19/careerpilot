package com.placify.proctoring.service;

import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.storage.FileStorageService;
import com.placify.proctoring.config.ProctoringProperties;
import com.placify.proctoring.domain.ProctoringSession;
import com.placify.proctoring.domain.Violation;
import com.placify.proctoring.domain.ViolationType;
import com.placify.proctoring.repository.ProctoringSessionRepository;
import com.placify.proctoring.vision.FaceMatcher;
import com.placify.proctoring.vision.FrameAnalyzer;
import com.placify.proctoring.vision.PhoneDetector;
import com.placify.proctoring.vision.SessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Analyses queued camera frames off the request thread, so slow image analysis
 * never delays the exam. Frames without problems are deleted immediately;
 * frames with a violation are kept as evidence.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FrameWorker {

    static final String QUEUE = "proctoring.frames";

    private final ProctoringSessionRepository sessions;
    private final ViolationService violationService;
    private final FrameAnalyzer analyzer;
    private final PhoneDetector phoneDetector;
    private final FaceMatcher faceMatcher;
    private final FileStorageService storage;
    private final ProctoringProperties props;

    @Bean
    Queue proctoringFrameQueue() {
        // frames older than 30 s are useless; let RabbitMQ drop them under load
        return QueueBuilder.durable(QUEUE).ttl(30_000).build();
    }

    @Bean
    Binding proctoringFrameBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(proctoringFrameQueue()).to(placifyExchange).with(RoutingKeys.PROCTORING_FRAME);
    }

    @RabbitListener(queues = QUEUE)
    public void analyse(Events.FrameAnalysisRequest request) {
        boolean keep = false;
        try {
            ProctoringSession s = sessions.findById(request.sessionId()).orElse(null);
            if (s == null || s.getStatus() != ProctoringSession.Status.ACTIVE) {
                return;
            }
            if (request.capturedAt() != null && Duration.between(request.capturedAt(), LocalDateTime.now()).getSeconds() > 30) {
                return; // stale frame
            }
            byte[] bytes = storage.read(request.snapshotPath());
            SessionState state = violationService.state(s.getId());

            List<FrameAnalyzer.Finding> findings;
            synchronized (state) {
                state.frameCounter++;
                findings = new ArrayList<>(analyzer.analyze(bytes, state));
            }

            if (s.checkEnabled(ViolationType.PHONE_DETECTED)) {
                Optional<Double> phone = phoneDetector.detect(bytes);
                phone.ifPresent(p -> findings.add(new FrameAnalyzer.Finding(ViolationType.PHONE_DETECTED,
                        String.format("Phone detected (confidence %.0f%%)", p * 100))));
            }

            // periodic identity re-check against the photo taken at the start
            boolean faceVisible = findings.stream().noneMatch(f -> f.type() == ViolationType.NO_FACE
                    || f.type() == ViolationType.CAMERA_BLOCKED || f.type() == ViolationType.MULTIPLE_FACES);
            if (faceVisible && state.referencePhoto != null && s.checkEnabled(ViolationType.DIFFERENT_PERSON)
                    && state.frameCounter % Math.max(1, props.identityCheckEvery()) == 0) {
                double sim = faceMatcher.similarity(state.referencePhoto, bytes);
                if (sim >= 0 && sim < props.identityThreshold()) {
                    findings.add(new FrameAnalyzer.Finding(ViolationType.DIFFERENT_PERSON,
                            String.format("Face similarity %.2f", sim)));
                }
            }

            for (FrameAnalyzer.Finding f : findings) {
                boolean recorded = violationService.record(s.getId(), f.type(), f.detail(), Violation.Source.CAMERA,
                        keep ? null : request.snapshotPath());
                if (recorded && !keep) {
                    keep = true; // this frame is now evidence for its first violation
                }
            }
            sessions.findById(s.getId()).ifPresent(fresh -> {
                fresh.setFramesAnalysed(fresh.getFramesAnalysed() + 1);
                sessions.save(fresh);
            });
        } catch (Exception ex) {
            log.warn("Frame analysis failed for session {}: {}", request.sessionId(), ex.getMessage());
        } finally {
            if (!keep) {
                storage.delete(request.snapshotPath());
            }
        }
    }
}
