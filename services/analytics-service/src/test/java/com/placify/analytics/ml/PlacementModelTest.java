package com.placify.analytics.ml;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Trains the real Tribuo model on the synthetic history and checks it learned sensible behaviour. */
class PlacementModelTest {

    @Test
    void learnsThatPreparationAndGradesRaisePlacementChance() {
        List<PlacementModel.Training> data = SyntheticHistory.generate(800, 42).stream()
                .map(r -> new PlacementModel.Training(r.features(), r.placed())).toList();
        PlacementModel model = new PlacementModel();
        PlacementModel.ModelInfo info = model.train(data);

        assertThat(info.trained()).isTrue();
        assertThat(info.accuracy()).isGreaterThan(0.6);

        double strong = model.probability(new Features(9.2, 0, 85, 80, 85, 80, 70));
        double weak = model.probability(new Features(6.1, 3, 15, 30, 40, 0, 0));
        assertThat(strong).isGreaterThan(0.6).isGreaterThan(weak);
        assertThat(weak).isLessThan(0.4);
    }

    @Test
    void fallsBackToFormulaWhenUntrained() {
        PlacementModel model = new PlacementModel();
        double p = model.probability(new Features(8, 0, 60, 60, 60, 60, 30));
        assertThat(p).isBetween(0.0, 1.0);
    }

    @Test
    void syntheticHistoryIsDeterministicAndBalanced() {
        var a = SyntheticHistory.generate(500, 42);
        var b = SyntheticHistory.generate(500, 42);
        assertThat(a).isEqualTo(b);
        long placed = a.stream().filter(SyntheticHistory.Row::placed).count();
        assertThat(placed).isBetween(100L, 400L);
    }
}
