package com.placify.submission.repository;

import com.placify.submission.domain.SubmissionToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionTokenRepository extends JpaRepository<SubmissionToken, String> {

    List<SubmissionToken> findByStatusInAndExpiresAtAfter(Collection<SubmissionToken.Status> statuses, LocalDateTime time);

    List<SubmissionToken> findByStatusInAndExpiresAtBefore(Collection<SubmissionToken.Status> statuses, LocalDateTime time);

    Optional<SubmissionToken> findFirstBySubmissionIdAndTestcaseId(String submissionId, String testcaseId);

    List<SubmissionToken> findBySubmissionId(String submissionId);
}
