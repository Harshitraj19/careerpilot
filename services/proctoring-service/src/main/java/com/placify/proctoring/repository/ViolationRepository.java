package com.placify.proctoring.repository;

import com.placify.proctoring.domain.Violation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ViolationRepository extends JpaRepository<Violation, Long> {

    List<Violation> findBySessionIdOrderByOccurredAtAsc(Long sessionId);

    List<Violation> findTop5BySessionIdOrderByOccurredAtDesc(Long sessionId);

    List<Violation> findBySnapshotPathIsNotNullAndOccurredAtBefore(LocalDateTime time);
}
