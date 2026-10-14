package com.placify.assessment.repository;

import com.placify.assessment.domain.AttemptQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Long> {

    List<AttemptQuestion> findByAttemptIdOrderByOrderIndexAsc(Long attemptId);

    Optional<AttemptQuestion> findFirstByAttemptIdAndRefId(Long attemptId, String refId);
}
