package com.placify.interview.repository;

import com.placify.interview.domain.InterviewEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    Optional<InterviewEvaluation> findByRoundIdAndStudentUserId(Long roundId, String studentUserId);

    List<InterviewEvaluation> findByRoundIdOrderByOverallScoreDesc(Long roundId);

    List<InterviewEvaluation> findByStudentUserIdOrderByCreatedAtDesc(String studentUserId);
}
