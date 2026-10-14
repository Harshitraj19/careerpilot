package com.placify.placement.messaging;

import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.placement.service.RoundProgressionService;
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

/**
 * Applies test results and interview evaluations to the selection rounds,
 * so candidates move forward automatically.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PlacementEventListener {

    static final String ASSESSMENT_QUEUE = "placement.assessment-completed";
    static final String INTERVIEW_QUEUE = "placement.interview-evaluated";

    private final RoundProgressionService progression;

    @Bean
    Queue placementAssessmentQueue() {
        return QueueBuilder.durable(ASSESSMENT_QUEUE).build();
    }

    @Bean
    Queue placementInterviewQueue() {
        return QueueBuilder.durable(INTERVIEW_QUEUE).build();
    }

    @Bean
    Binding placementAssessmentBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(placementAssessmentQueue()).to(placifyExchange).with(RoutingKeys.ASSESSMENT_COMPLETED);
    }

    @Bean
    Binding placementInterviewBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(placementInterviewQueue()).to(placifyExchange).with(RoutingKeys.INTERVIEW_EVALUATED);
    }

    @RabbitListener(queues = ASSESSMENT_QUEUE)
    public void onAssessmentCompleted(Events.AssessmentCompletedEvent e) {
        if (e.roundId() == null) {
            return; // practice or TPO test not linked to a job round
        }
        log.info("Test result for round {} student {}: {}% passed={} flagged={}", e.roundId(), e.studentUserId(),
                e.percent(), e.passed(), e.proctoringFlagged());
        progression.onTestCompleted(e.roundId(), e.studentUserId(), e.percent(), e.passed(), e.proctoringFlagged(), e.trustScore());
    }

    @RabbitListener(queues = INTERVIEW_QUEUE)
    public void onInterviewEvaluated(Events.InterviewEvaluatedEvent e) {
        if (e.roundId() == null) {
            return;
        }
        progression.onInterviewEvaluated(e.roundId(), e.studentUserId(), e.result(), e.score(), e.feedback());
    }
}
