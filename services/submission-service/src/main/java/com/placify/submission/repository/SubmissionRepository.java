package com.placify.submission.repository;

import com.placify.submission.domain.Submission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, String> {

    List<Submission> findByUserIdOrderBySubmissionTimeDesc(String userId, Pageable pageable);

    List<Submission> findByUserIdAndQuestionIdOrderBySubmissionTimeDesc(String userId, String questionId);

    List<Submission> findByTestIdAndModeOrderBySubmissionTimeDesc(Long testId, Submission.Mode mode);

    List<Submission> findByContestIdAndModeOrderBySubmissionTimeDesc(Long contestId, Submission.Mode mode);

    List<Submission> findByAttemptIdOrderBySubmissionTimeDesc(Long attemptId);

    List<Submission> findByIdIn(Collection<String> ids);

    long countByUserId(String userId);
}
