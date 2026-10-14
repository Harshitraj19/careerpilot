package com.placify.submission.repository;

import com.placify.submission.domain.SubmissionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, Long> {

    List<SubmissionResult> findBySubmissionIdOrderByIdAsc(String submissionId);

    Optional<SubmissionResult> findFirstBySubmissionIdAndTestcaseId(String submissionId, String testcaseId);

    long countBySubmissionId(String submissionId);
}
