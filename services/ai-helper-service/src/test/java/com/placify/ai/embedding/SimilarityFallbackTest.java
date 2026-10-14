package com.placify.ai.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SimilarityFallbackTest {

    @Test
    void termCosineRanksRelatedTextHigher() {
        String jd = "Java Spring Boot developer with SQL and REST experience";
        double related = EmbeddingService.termCosine(jd, "Built REST services in Java and Spring Boot backed by SQL");
        double unrelated = EmbeddingService.termCosine(jd, "Watercolour painting and classical music");
        assertThat(related).isGreaterThan(unrelated);
        assertThat(EmbeddingService.termCosine(jd, jd)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosineOfVectors() {
        assertThat(EmbeddingService.cosine(new float[] {1, 0}, new float[] {1, 0})).isCloseTo(1.0, within(1e-6));
        assertThat(EmbeddingService.cosine(new float[] {1, 0}, new float[] {0, 1})).isCloseTo(0.0, within(1e-6));
    }
}
