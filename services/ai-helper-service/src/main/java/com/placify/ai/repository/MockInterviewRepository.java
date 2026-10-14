package com.placify.ai.repository;

import com.placify.ai.domain.MockInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MockInterviewRepository extends JpaRepository<MockInterview, Long> {

    List<MockInterview> findByUserIdOrderByCreatedAtDesc(String userId);

    List<MockInterview> findByUserIdAndStatus(String userId, MockInterview.Status status);
}
