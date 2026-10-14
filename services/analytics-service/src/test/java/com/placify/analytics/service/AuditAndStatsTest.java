package com.placify.analytics.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAndStatsTest {

    @Test
    void csvCellsAreQuotedAndFormulaSafe() {
        assertThat(AuditService.csvCell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(AuditService.csvCell("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(AuditService.csvCell(null)).isEmpty();
    }

    @Test
    void ctcStatistics() {
        var s = DashboardService.ctc(List.of(new BigDecimal("4"), new BigDecimal("10"), new BigDecimal("6"), new BigDecimal("8")));
        assertThat(s.median()).isEqualByComparingTo("7");
        assertThat(s.highest()).isEqualByComparingTo("10");
        assertThat(s.average()).isEqualByComparingTo("7");
    }

    @Test
    void topCountsAreSortedAndLimited() {
        var top = DashboardService.top(Map.of("a", 1L, "b", 5L, "c", 3L), 2);
        assertThat(top).extracting(n -> n.name()).containsExactly("b", "c");
        assertThat(DashboardService.pct(1, 3)).isEqualTo(33.3);
    }
}
