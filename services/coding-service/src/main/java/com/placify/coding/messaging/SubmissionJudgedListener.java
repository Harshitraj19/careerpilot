package com.placify.coding.messaging;

import com.placify.coding.service.ContestService;
import com.placify.coding.service.PracticeService;
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
 * Updates practice ratings and contest scores when a submission is judged.
 */
@Configuration
@RequiredArgsConstructor
public class SubmissionJudgedListener {

    static final String QUEUE = "coding.submission-judged";

    private final PracticeService practice;
    private final ContestService contests;

    @Bean
    Queue codingJudgedQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding codingJudgedBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(codingJudgedQueue()).to(placifyExchange).with(RoutingKeys.SUBMISSION_JUDGED);
    }

    @RabbitListener(queues = QUEUE)
    public void onJudged(Events.SubmissionJudgedEvent e) {
        boolean accepted = "accepted".equalsIgnoreCase(e.verdict());
        switch (e.mode() == null ? "PRACTICE" : e.mode()) {
            case "CONTEST" -> {
                if (e.contestId() != null) {
                    contests.onContestSubmission(e.contestId(), e.userId(), e.questionId(), accepted);
                }
            }
            case "PRACTICE" -> practice.onPracticeSubmission(e.userId(), e.questionId(), accepted);
            default -> {
                // ASSESSMENT submissions are graded by assessment-service
            }
        }
    }
}
