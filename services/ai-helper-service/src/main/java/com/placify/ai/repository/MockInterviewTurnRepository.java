package com.placify.ai.repository;

import com.placify.ai.domain.MockInterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockInterviewTurnRepository extends JpaRepository<MockInterviewTurn, Long> {

    List<MockInterviewTurn> findByInterviewIdOrderBySeqAsc(Long interviewId);
}
