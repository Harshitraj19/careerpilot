package com.placify.analytics.repository;

import com.placify.analytics.domain.ReadinessSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReadinessSnapshotRepository extends JpaRepository<ReadinessSnapshot, String> {

    List<ReadinessSnapshot> findByUserIdIn(Collection<String> userIds);
}
