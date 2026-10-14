package com.placify.placement.repository;

import com.placify.placement.domain.PlacementPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<PlacementPolicy, Long> {
}
