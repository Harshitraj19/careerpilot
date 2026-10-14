package com.placify.assessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.repository.McqQuestionRepository;
import com.placify.common.demo.DemoData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Approved aptitude bank (DEMO_DATA=true): 132 questions, 11 for every topic and difficulty, loaded
 * from {@code demo/aptitude-bank.json}. Questions already in the database (same text) are skipped,
 * so new questions are added to existing installations too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoMcqSeeder implements ApplicationRunner {

    private final McqQuestionRepository mcqs;
    private final ObjectMapper mapper;

    record BankQuestion(McqQuestion.Topic topic, McqQuestion.Difficulty difficulty, String subtopic, String question,
                        List<String> options, int answer, String explanation) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<BankQuestion> bank;
        try (InputStream in = new ClassPathResource("demo/aptitude-bank.json").getInputStream()) {
            bank = mapper.readValue(in, new TypeReference<>() {
            });
        }
        Set<String> existing = new HashSet<>(mcqs.allTexts());
        int added = 0;
        for (BankQuestion q : bank) {
            if (!existing.add(q.question())) {
                continue;
            }
            mcqs.save(McqQuestion.builder().topic(q.topic()).subtopic(q.subtopic()).difficulty(q.difficulty())
                    .text(q.question()).optionsJson(mapper.writeValueAsString(q.options())).correctIndex(q.answer())
                    .explanation(q.explanation()).source(McqQuestion.Source.MANUAL).approved(true)
                    .createdBy(DemoData.TPO_ID).build());
            added++;
        }
        if (added > 0) {
            log.info("Added {} aptitude questions to the MCQ bank", added);
        }
    }
}
