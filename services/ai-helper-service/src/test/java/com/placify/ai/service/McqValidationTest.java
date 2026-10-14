package com.placify.ai.service;

import com.placify.ai.web.AiDtos.GeneratedMcq;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McqValidationTest {

    @Test
    void dropsMalformedAndDuplicateQuestions() {
        List<GeneratedMcq> raw = Arrays.asList(
                new GeneratedMcq("What is 2+2?", List.of("3", "4", "5", "6"), 1, "basic"),
                new GeneratedMcq("what is 2+2? ", List.of("1", "4", "5", "6"), 1, "duplicate"),
                new GeneratedMcq("Three options only", List.of("a", "b", "c"), 0, ""),
                new GeneratedMcq("Repeated options", List.of("a", "a", "b", "c"), 0, ""),
                new GeneratedMcq("Bad index", List.of("a", "b", "c", "d"), 7, ""),
                null,
                new GeneratedMcq("Valid two", List.of("w", "x", "y", "z"), 3, "ok"));
        List<GeneratedMcq> ok = AiTasksService.validMcqs(raw, 10);
        assertThat(ok).extracting(GeneratedMcq::question).containsExactly("What is 2+2?", "Valid two");
    }

    @Test
    void respectsTheLimit() {
        List<GeneratedMcq> raw = List.of(
                new GeneratedMcq("q1", List.of("a", "b", "c", "d"), 0, ""),
                new GeneratedMcq("q2", List.of("a", "b", "c", "d"), 0, ""));
        assertThat(AiTasksService.validMcqs(raw, 1)).hasSize(1);
    }
}
