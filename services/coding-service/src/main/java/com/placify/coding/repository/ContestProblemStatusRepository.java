package com.placify.coding.repository;

import com.placify.coding.domain.ContestProblemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestProblemStatusRepository extends JpaRepository<ContestProblemStatus, Long> {

    Optional<ContestProblemStatus> findByContestIdAndUserIdAndQuestionId(Long contestId, String userId, String questionId);

    List<ContestProblemStatus> findByContestIdAndUserId(Long contestId, String userId);
}
