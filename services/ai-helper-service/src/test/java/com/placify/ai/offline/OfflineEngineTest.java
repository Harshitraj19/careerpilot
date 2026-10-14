package com.placify.ai.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.ai.web.AiDtos.GeneratedMcq;
import com.placify.ai.web.AiDtos.ParsedResume;
import com.placify.ai.web.AiDtos.ResumeFeedback;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineEngineTest {

    private final InterviewBank bank = new InterviewBank(new ObjectMapper());

    private static final String RESUME = """
            Aarav Sharma
            aarav@example.com | github.com/aarav-dev | linkedin.com/in/aarav-sharma
            SUMMARY
            Final-year CSE student who builds backend systems with Java and Spring Boot.
            SKILLS
            Java, Spring Boot, MySQL, Docker, Data Structures, Git
            PROJECTS
            Campus Connect | Java, Spring Boot, MySQL
            • Built a study-group finder used by 300+ students, cutting search time by 40%.
            Expense Tracker (React, Node.js)
            • Personal finance app with charts and monthly reports.
            EXPERIENCE
            Backend Intern, TechNova (May-July 2025)
            • Wrote REST APIs for the payments team.
            CERTIFICATIONS
            Oracle Certified Java Programmer
            EDUCATION
            B.Tech Computer Science, 2026, CGPA 8.9
            """;

    @Test
    void bankLoadsAndEveryQuestionHasKeyPoints() {
        assertThat(bank.all()).hasSizeGreaterThan(40);
        assertThat(bank.all()).allSatisfy(q -> assertThat(q.points()).isNotEmpty());
    }

    @Test
    void strongAnswerScoresHigherThanWeakAnswer() {
        var q = bank.find("What is the difference between a process and a thread?").orElseThrow();
        String strong = "A process has its own address space and memory, so processes are isolated from each other. "
                + "Threads of the same process share the same memory and heap, which makes them lightweight. Because of that a "
                + "context switch between threads is cheaper. Processes communicate with IPC such as pipes, while threads need "
                + "synchronization with locks or a mutex. For example, in my project the web server used a thread pool.";
        String weak = "A thread is smaller than a process.";
        int high = AnswerEvaluator.evaluate(q, q.question(), strong).score();
        int low = AnswerEvaluator.evaluate(q, q.question(), weak).score();
        assertThat(high).isGreaterThanOrEqualTo(8);
        assertThat(low).isLessThanOrEqualTo(2);
    }

    @Test
    void parsesSectionsSkillsProjectsAndLinks() {
        ParsedResume r = new ResumeAnalyzer().parse(RESUME);
        assertThat(r.skills()).contains("Java", "Spring Boot", "SQL", "Docker", "Git");
        assertThat(r.projects()).extracting("title").contains("Campus Connect", "Expense Tracker");
        assertThat(r.certifications()).contains("Oracle Certified Java Programmer");
        assertThat(r.githubUrl()).isEqualTo("https://github.com/aarav-dev");
        assertThat(r.linkedinUrl()).startsWith("https://linkedin.com/in/aarav-sharma");
        assertThat(r.internships()).contains("Backend Intern");
        assertThat(r.summary()).contains("backend systems");
    }

    @Test
    void feedbackNamesMissingSkills() {
        ResumeFeedback f = new ResumeAnalyzer().feedback(RESUME, "We need Java, Spring Boot, Kubernetes and Kafka experience.");
        assertThat(f.fitScore()).isBetween(1, 100);
        assertThat(String.join(" ", f.weaknesses())).contains("Kubernetes", "Kafka");
    }

    @Test
    void generatedMcqsAreWellFormedAndUnique() {
        McqFactory factory = new McqFactory();
        for (String topic : List.of("QUANTITATIVE", "LOGICAL", "VERBAL", "TECHNICAL")) {
            List<GeneratedMcq> list = factory.generate(topic, "MEDIUM", 8);
            assertThat(list).hasSize(8);
            assertThat(list).allSatisfy(q -> {
                assertThat(q.options()).hasSize(4);
                assertThat(new HashSet<>(q.options())).hasSize(4);
                assertThat(q.correctIndex()).isBetween(0, 3);
            });
            assertThat(list.stream().map(GeneratedMcq::question).distinct()).hasSize(8);
        }
    }

    @Test
    void estimatesLoopComplexity() {
        String nested = "for (int i = 0; i < n; i++) { for (int j = 0; j < n; j++) { s += a[i] * a[j]; } }";
        String single = "for (int i = 0; i < n; i++) { best = Math.max(best, a[i]); }";
        String python = "for i in range(n):\n    for j in range(n):\n        s += 1\n";
        assertThat(CodeAnalyzer.estimate(nested, "Java")[0]).isEqualTo("O(n²)");
        assertThat(CodeAnalyzer.estimate(single, "Java")[0]).isEqualTo("O(n)");
        assertThat(CodeAnalyzer.estimate(python, "Python 3")[0]).isEqualTo("O(n²)");
    }
}
