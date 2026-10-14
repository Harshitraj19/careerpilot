package com.placify.interview.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewScoringTest {

    @Test
    void overallScoreIsWeightedOnHundred() {
        assertThat(InterviewEvaluation.overall(10, 10, 10, 10)).isEqualTo(100);
        assertThat(InterviewEvaluation.overall(1, 1, 1, 1)).isEqualTo(10);
        assertThat(InterviewEvaluation.overall(10, 5, 5, 5)).isGreaterThan(InterviewEvaluation.overall(5, 5, 5, 10));
    }

    @Test
    void companyNamesAreNormalisedForGrouping() {
        assertThat(InterviewExperience.keyOf("  Tata   Consultancy Services ")).isEqualTo("tata consultancy services");
    }
}
