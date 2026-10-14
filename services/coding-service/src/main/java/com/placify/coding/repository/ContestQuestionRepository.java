package com.placify.coding.repository;

import com.placify.coding.domain.ContestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestQuestionRepository extends JpaRepository<ContestQuestion, Long> {

    List<ContestQuestion> findByContestIdOrderByOrderIndexAsc(Long contestId);

    Optional<ContestQuestion> findByContestIdAndQuestionId(Long contestId, String questionId);

    void deleteByContestId(Long contestId);

    boolean existsByQuestionId(String questionId);
}
