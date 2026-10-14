package com.placify.notification.repository;

import com.placify.notification.domain.QueryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryMessageRepository extends JpaRepository<QueryMessage, Long> {

    List<QueryMessage> findByQueryIdOrderByCreatedAtAsc(Long queryId);
}
