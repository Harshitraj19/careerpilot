package com.placify.profile.ats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AtsScorerTest {

    private static final String JD = """
            We are hiring a Java backend developer. Required: Java, Spring Boot, REST APIs, MySQL, Docker,
            microservices and unit testing with JUnit. Experience with Kafka or RabbitMQ is a plus.
            """;

    private static final String GOOD = """
            Aarav Sharma | aarav@example.com | +91 9000000001 | linkedin.com/in/aarav | github.com/aarav
            SUMMARY Backend developer focused on Java and Spring Boot microservices.
            SKILLS Java, Spring Boot, REST APIs, MySQL, Docker, JUnit, RabbitMQ
            EXPERIENCE Backend intern: built 6 REST APIs used by 20k users and reduced latency by 35%.
            PROJECTS Placify - Spring Boot microservices with Docker and MySQL.
            EDUCATION B.Tech CSE, CGPA 8.9
            CERTIFICATIONS Oracle Java SE 17
            """;

    private static final String WEAK = "John. I like painting and music. Hobbies: travel.";

    @Test
    void relevantResumeScoresHigherThanIrrelevantOne() {
        AtsScorer.Result good = AtsScorer.score(GOOD, JD, -1);
        AtsScorer.Result weak = AtsScorer.score(WEAK, JD, -1);
        assertThat(good.score()).isGreaterThan(weak.score() + 30);
        assertThat(good.score()).isBetween(0, 100);
    }

    @Test
    void reportsMatchedAndMissingKeywords() {
        AtsScorer.Result r = AtsScorer.score(GOOD.replace("Docker", "containers"), JD, -1);
        assertThat(r.matchedKeywords()).isNotEmpty();
        assertThat(r.missingKeywords()).isNotEmpty();
        assertThat(r.tips()).isNotNull();
    }

    @Test
    void stemmingTreatsWordFormsAlike() {
        assertThat(TextAnalyzer.stem("testing")).isEqualTo(TextAnalyzer.stem("tests"));
    }
}
