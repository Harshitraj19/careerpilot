package com.placify.placement.repository;

import com.placify.placement.domain.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findByJobIdOrderBySequenceAsc(Long jobId);

    List<Round> findByJobIdIn(Collection<Long> jobIds);

    Optional<Round> findFirstByJobIdAndSequenceGreaterThanOrderBySequenceAsc(Long jobId, int sequence);

    Optional<Round> findFirstByJobIdOrderBySequenceAsc(Long jobId);

    List<Round> findByAssessmentTestIdIsNotNullAndJobIdIn(Collection<Long> jobIds);
}
