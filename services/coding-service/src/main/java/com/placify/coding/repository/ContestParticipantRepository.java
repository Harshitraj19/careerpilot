package com.placify.coding.repository;

import com.placify.coding.domain.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Long> {

    Optional<ContestParticipant> findByContestIdAndUserId(Long contestId, String userId);

    List<ContestParticipant> findByContestIdOrderByScoreDescPenaltyMinutesAsc(Long contestId);

    long countByContestId(Long contestId);

    List<ContestParticipant> findByUserId(String userId);
}
