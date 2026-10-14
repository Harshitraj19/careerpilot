package com.placify.coding.repository;

import com.placify.coding.domain.UserQuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserQuestionStatusRepository extends JpaRepository<UserQuestionStatus, Long> {

    Optional<UserQuestionStatus> findByUserIdAndQuestionId(String userId, String questionId);

    List<UserQuestionStatus> findByUserId(String userId);

    List<UserQuestionStatus> findByUserIdAndSolvedTrueOrderByFirstSolvedAtDesc(String userId);
}
