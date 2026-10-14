package com.placify.analytics.messaging;

import com.placify.analytics.domain.PlacementRecord;
import com.placify.analytics.repository.PlacementRecordRepository;
import com.placify.analytics.service.AuditService;
import com.placify.analytics.service.ReadinessService;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
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

import java.time.LocalDateTime;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AnalyticsEventListener {

    static final String AUDIT_QUEUE = "analytics.audit";
    static final String PLACED_QUEUE = "analytics.student-placed";
    static final String ASSESSMENT_QUEUE = "analytics.assessment-completed";

    private final AuditService audit;
    private final PlacementRecordRepository records;
    private final ReadinessService readiness;

    @Bean
    Queue analyticsAuditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    Queue analyticsPlacedQueue() {
        return QueueBuilder.durable(PLACED_QUEUE).build();
    }

    @Bean
    Queue analyticsAssessmentQueue() {
        return QueueBuilder.durable(ASSESSMENT_QUEUE).build();
    }

    @Bean
    Binding analyticsAuditBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(analyticsAuditQueue()).to(placifyExchange).with(RoutingKeys.AUDIT);
    }

    @Bean
    Binding analyticsPlacedBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(analyticsPlacedQueue()).to(placifyExchange).with(RoutingKeys.STUDENT_PLACED);
    }

    @Bean
    Binding analyticsAssessmentBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(analyticsAssessmentQueue()).to(placifyExchange).with(RoutingKeys.ASSESSMENT_COMPLETED);
    }

    @RabbitListener(queues = AUDIT_QUEUE)
    public void onAudit(Events.AuditEvent e) {
        audit.record(e);
    }

    @RabbitListener(queues = PLACED_QUEUE)
    public void onPlaced(Events.StudentPlacedEvent e) {
        if (e.offerId() == null || records.existsByOfferId(e.offerId())) {
            return;
        }
        records.save(PlacementRecord.builder().studentUserId(e.studentUserId()).offerId(e.offerId())
                .companyName(e.companyName() == null ? "Unknown" : e.companyName()).ctcLpa(e.ctcLpa()).tier(e.tier())
                .placedAt(LocalDateTime.now()).build());
    }

    /** A finished test changes the aptitude part of readiness: refresh it. */
    @RabbitListener(queues = ASSESSMENT_QUEUE)
    public void onAssessment(Events.AssessmentCompletedEvent e) {
        try {
            readiness.compute(e.studentUserId());
        } catch (Exception ex) {
            log.debug("Readiness refresh after test failed: {}", ex.getMessage());
        }
    }
}
