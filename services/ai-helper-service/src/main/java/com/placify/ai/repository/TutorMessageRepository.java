package com.placify.ai.repository;

import com.placify.ai.domain.TutorMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TutorMessageRepository extends JpaRepository<TutorMessage, Long> {

    List<TutorMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    List<TutorMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);
}
