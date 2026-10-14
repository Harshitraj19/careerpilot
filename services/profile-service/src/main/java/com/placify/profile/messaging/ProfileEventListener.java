package com.placify.profile.messaging;

import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.profile.service.CompanyProfileService;
import com.placify.profile.service.StudentProfileService;
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
 * Creates profiles when auth-service imports a student or approves a company.
 */
@Configuration
@RequiredArgsConstructor
public class ProfileEventListener {

    static final String STUDENT_QUEUE = "profile.student-imported";
    static final String COMPANY_QUEUE = "profile.company-approved";

    private final StudentProfileService students;
    private final CompanyProfileService companies;

    @Bean
    Queue profileStudentQueue() {
        return QueueBuilder.durable(STUDENT_QUEUE).build();
    }

    @Bean
    Queue profileCompanyQueue() {
        return QueueBuilder.durable(COMPANY_QUEUE).build();
    }

    @Bean
    Binding profileStudentBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(profileStudentQueue()).to(placifyExchange).with(RoutingKeys.STUDENT_IMPORTED);
    }

    @Bean
    Binding profileCompanyBinding(TopicExchange placifyExchange) {
        return BindingBuilder.bind(profileCompanyQueue()).to(placifyExchange).with(RoutingKeys.COMPANY_APPROVED);
    }

    @RabbitListener(queues = STUDENT_QUEUE)
    public void onStudentImported(Events.StudentImportedEvent event) {
        students.onStudentImported(event);
    }

    @RabbitListener(queues = COMPANY_QUEUE)
    public void onCompanyApproved(Events.CompanyApprovedEvent event) {
        companies.onCompanyApproved(event);
    }
}
