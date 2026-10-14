package com.placify.assessment.repository;

import com.placify.assessment.domain.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TestRepository extends JpaRepository<Test, Long> {

    boolean existsByTitle(String title);

    boolean existsByRoundId(Long roundId);


    List<Test> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    List<Test> findAllByOrderByCreatedAtDesc();

    List<Test> findByStatusAndWindowEndAfterOrderByWindowStartAsc(Test.Status status, LocalDateTime time);

    List<Test> findByStatusAndWindowEndBefore(Test.Status status, LocalDateTime time);

    List<Test> findByStatusAndReminderSentFalseAndWindowStartBetween(Test.Status status, LocalDateTime from, LocalDateTime to);

    List<Test> findByIdIn(java.util.Collection<Long> ids);
}
