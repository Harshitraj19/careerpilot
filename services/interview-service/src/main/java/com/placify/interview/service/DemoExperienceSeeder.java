package com.placify.interview.service;

import com.placify.common.demo.DemoData;
import com.placify.interview.domain.InterviewExperience;
import com.placify.interview.repository.InterviewExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Approved sample interview experiences for the prep hub (DEMO_DATA=true). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoExperienceSeeder implements ApplicationRunner {

    private final InterviewExperienceRepository experiences;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (experiences.count() > 0) {
            return;
        }
        DemoData.Student a = DemoData.STUDENTS.get(1);
        DemoData.Student b = DemoData.STUDENTS.get(5);
        save(a, "TechNova Solutions", "Software Development Engineer", InterviewExperience.Outcome.SELECTED, InterviewExperience.Difficulty.MEDIUM,
                "Online test (aptitude + 2 coding) → Technical interview → HR",
                "The online test had 20 aptitude questions and two coding problems: one on arrays (Kadane) and one on stacks. "
                        + "In the technical round they asked me to explain my final-year project end to end, then design a URL shortener, "
                        + "and write SQL for the second highest salary. They care a lot about OOP basics and clean code.",
                "Kadane's algorithm; balanced brackets; difference between HashMap and ConcurrentHashMap; REST vs gRPC; 2nd highest salary SQL",
                "Revise Spring Boot basics and be ready to draw your project's architecture.", false);
        save(b, "TechNova Solutions", "Software Development Engineer", InterviewExperience.Outcome.REJECTED, InterviewExperience.Difficulty.MEDIUM,
                "Online test → Technical interview",
                "I cleared the online test easily but struggled in the technical interview with a dynamic programming question "
                        + "(longest increasing subsequence in O(n log n)). The interviewer was friendly and gave hints. "
                        + "Also asked about deadlocks and normalisation in DBMS.",
                "LIS in O(n log n); deadlock conditions; 3NF vs BCNF", "Practise DP patterns, not just easy array problems.", true);
        save(a, "FinEdge Analytics", "Data Analyst Intern", InterviewExperience.Outcome.SELECTED, InterviewExperience.Difficulty.EASY,
                "Aptitude test → Case study → Interview",
                "Aptitude test was mostly percentages, ratios and probability. The case study asked us to find why failed payments "
                        + "increased in one city; we used SQL group-bys and a simple chart. Final interview was about pandas and statistics.",
                "Probability with dice; SQL GROUP BY / HAVING; mean vs median for skewed data", "Think aloud during the case study.", false);
    }

    private void save(DemoData.Student s, String company, String role, InterviewExperience.Outcome outcome,
                      InterviewExperience.Difficulty difficulty, String rounds, String content, String questions, String tips,
                      boolean anonymous) {
        experiences.save(InterviewExperience.builder().authorUserId(s.id()).authorName(s.name()).anonymous(anonymous)
                .companyName(company).companyKey(InterviewExperience.keyOf(company)).jobRole(role).year(LocalDateTime.now().getYear() - 1)
                .result(outcome).difficulty(difficulty).roundsSummary(rounds).content(content).questionsAsked(questions).tips(tips)
                .status(InterviewExperience.Status.APPROVED).reviewedBy(DemoData.TPO_ID).reviewedAt(LocalDateTime.now()).upvotes(3)
                .build());
    }
}
