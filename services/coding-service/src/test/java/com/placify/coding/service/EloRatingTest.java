package com.placify.coding.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EloRatingTest {

    @Test
    void equalRatingsGiveFiftyPercent() {
        assertThat(EloRating.expected(1400, 1400)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void solvingAHarderProblemGainsMoreThanAnEasyOne() {
        int hard = EloRating.update(1200, 1800, 1, 32) - 1200;
        int easy = EloRating.update(1200, 1000, 1, 32) - 1200;
        assertThat(hard).isGreaterThan(easy).isPositive();
    }

    @Test
    void failingLowersRatingAndStaysInBounds() {
        assertThat(EloRating.update(1400, 1000, 0, 16)).isLessThan(1400);
        assertThat(EloRating.update(EloRating.MIN, 3000, 0, 32)).isEqualTo(EloRating.MIN);
        assertThat(EloRating.update(EloRating.MAX, 600, 1, 32)).isEqualTo(EloRating.MAX);
    }
}
