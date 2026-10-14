package com.placify.assessment.messaging;

import com.placify.assessment.service.AttemptService;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coding results inside tests and proctoring terminations.
 */
@Configuration
@RequiredArgsConstructor
public class AssessmentEventListener {

    static final String JUDGED_QUEUE = "assessment.submission-judged";
    static final String TERMINATE_QUEUE = "assessment.proctoring-terminate";

    private final AttemptService attempts;

    @Bean
    Queue assessmentJudgedQueue() {
        return QueueBuilder.durable(JUDGED_QUEUE).build();
    }

    @Bean
    Queue assessmentTerminateQueue() {
        return QueueBuilder.durable(TERMINATE_QUEUE).build();
    }

    @Bean
    Binding assessmentJudgedBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(assessmentJudgedQueue()).to(placifyExchange).with(RoutingKeys.SUBMISSION_JUDGED);
    }

    @Bean
    Binding assessmentTerminateBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(assessmentTerminateQueue()).to(placifyExchange).with(RoutingKeys.PROCTORING_TERMINATE);
    }

    @RabbitListener(queues = JUDGED_QUEUE)
    public void onJudged(Events.SubmissionJudgedEvent e) {
        if ("ASSESSMENT".equals(e.mode())) {
            attempts.onSubmissionJudged(e);
        }
    }

    @RabbitListener(queues = TERMINATE_QUEUE)
    public void onTerminate(Events.ProctoringTerminateEvent e) {
        attempts.onProctoringTerminate(e);
    }
}
