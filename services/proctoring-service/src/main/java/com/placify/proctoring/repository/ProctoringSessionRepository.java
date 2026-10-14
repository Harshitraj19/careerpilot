package com.placify.proctoring.repository;

import com.placify.proctoring.domain.ProctoringSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProctoringSessionRepository extends JpaRepository<ProctoringSession, Long> {

    Optional<ProctoringSession> findByAttemptId(Long attemptId);

    List<ProctoringSession> findByTestId(Long testId);

    /** Row lock so parallel frame workers update the counters one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProctoringSession s where s.id = :id")
    Optional<ProctoringSession> lockById(@Param("id") Long id);
}
