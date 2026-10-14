package com.placify.analytics.repository;

import com.placify.analytics.domain.HistoricalOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricalOutcomeRepository extends JpaRepository<HistoricalOutcome, Long> {

    long countBySource(String source);

    long countByBatch(Integer batch);
}
