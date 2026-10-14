package com.placify.assessment.repository;

import com.placify.assessment.domain.TestPoolItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TestPoolItemRepository extends JpaRepository<TestPoolItem, Long> {

    List<TestPoolItem> findByTestId(Long testId);

    List<TestPoolItem> findByTestIdAndKind(Long testId, TestPoolItem.Kind kind);

    List<TestPoolItem> findByTestIdIn(Collection<Long> testIds);

    void deleteByTestId(Long testId);
}
