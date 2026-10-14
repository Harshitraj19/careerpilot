package com.placify.auth.repository;

import com.placify.auth.domain.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Page<LoginHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<LoginHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfterAndSuccess(LocalDateTime after, boolean success);
}
