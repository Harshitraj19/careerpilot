package com.placify.placement.repository;

import com.placify.placement.domain.RoundResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoundResultRepository extends JpaRepository<RoundResult, Long> {

    List<RoundResult> findByRoundId(Long roundId);

    List<RoundResult> findByRoundIdAndStatusIn(Long roundId, Collection<RoundResult.Status> statuses);

    List<RoundResult> findByApplicationId(Long applicationId);

    List<RoundResult> findByApplicationIdIn(Collection<Long> applicationIds);

    List<RoundResult> findByRoundIdIn(Collection<Long> roundIds);

    Optional<RoundResult> findByRoundIdAndApplicationId(Long roundId, Long applicationId);

    Optional<RoundResult> findByRoundIdAndStudentUserId(Long roundId, String studentUserId);

    List<RoundResult> findByStudentUserId(String studentUserId);
}
