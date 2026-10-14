package com.placify.assessment.repository;

import com.placify.assessment.domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    Optional<Attempt> findByTestIdAndStudentUserId(Long testId, String studentUserId);

    List<Attempt> findByTestIdOrderByPercentDesc(Long testId);

    List<Attempt> findByStudentUserIdOrderByStartedAtDesc(String studentUserId);

    List<Attempt> findByStatusAndDeadlineAtBefore(Attempt.Status status, LocalDateTime time);

    long countByTestId(Long testId);
}
