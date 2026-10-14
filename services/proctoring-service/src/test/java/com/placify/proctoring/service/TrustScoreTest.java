package com.placify.proctoring.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustScoreTest {

    @Test
    void cleanSessionScoresHundred() {
        assertThat(ViolationService.trustScore(0, 0, 0)).isEqualTo(100);
    }

    @Test
    void severityIsWeighted() {
        assertThat(ViolationService.trustScore(1, 1, 1)).isEqualTo(100 - 15 - 7 - 2);
        assertThat(ViolationService.trustScore(1, 0, 0)).isLessThan(ViolationService.trustScore(0, 1, 0));
    }

    @Test
    void neverBelowZero() {
        assertThat(ViolationService.trustScore(20, 20, 20)).isZero();
    }
}
