package com.placify.ai.repository;

import com.placify.ai.domain.TutorSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TutorSessionRepository extends JpaRepository<TutorSession, Long> {

    Optional<TutorSession> findByUserIdAndQuestionId(String userId, String questionId);
}
