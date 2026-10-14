package com.placify.analytics.repository;

import com.placify.analytics.domain.PlacementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlacementRecordRepository extends JpaRepository<PlacementRecord, Long> {

    boolean existsByOfferId(Long offerId);

    List<PlacementRecord> findAllByOrderByPlacedAtAsc();
}
