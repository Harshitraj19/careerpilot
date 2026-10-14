package com.placify.analytics.service;

import com.placify.analytics.client.AnalyticsClients.AssessmentStats;
import com.placify.analytics.client.AnalyticsClients.CodingStats;
import com.placify.analytics.client.AnalyticsClients.MockStats;
import com.placify.analytics.client.AnalyticsClients.TopicStat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessCalculatorTest {

    @Test
    void emptyStudentScoresZeroAndGetsSuggestions() {
        var c = ReadinessCalculator.compute(null, null, null, 0, null);
        assertThat(c.total()).isZero();
        assertThat(ReadinessCalculator.suggestions(c)).hasSize(6);
    }

    @Test
    void wellPreparedStudentScoresHighAndComponentsStayWithinMaximum() {
        var coding = new CodingStats("u", 2100, 120, 300, 40, 50, 30, 8, 85.0);
        var apt = new AssessmentStats("u", List.of(new TopicStat("QUANTITATIVE", 200, 170, 85)), 85, 3, 82.0);
        var mock = new MockStats("u", 4, 80.0, 90);
        var c = ReadinessCalculator.compute(coding, apt, 88, 100, mock);
        assertThat(c.coding()).isLessThanOrEqualTo(30);
        assertThat(c.aptitude()).isLessThanOrEqualTo(20);
        assertThat(c.contests()).isLessThanOrEqualTo(10);
        assertThat(c.total()).isBetween(75, 100);
    }

    @Test
    void oneMockInterviewGetsOnlyPartialCredit() {
        double one = ReadinessCalculator.mock(new MockStats("u", 1, 90.0, 90));
        double three = ReadinessCalculator.mock(new MockStats("u", 3, 90.0, 90));
        assertThat(one).isLessThan(three);
    }

    @Test
    void suggestionsAreOrderedByPotentialGain() {
        var c = new ReadinessCalculator.Components(5, 18, 14, 9, 0, 9);
        var s = ReadinessCalculator.suggestions(c);
        assertThat(s.get(0).area()).isEqualTo("Coding");
        assertThat(s).extracting(ReadinessCalculator.Suggestion::area).doesNotContain("Aptitude", "Resume");
    }
}
