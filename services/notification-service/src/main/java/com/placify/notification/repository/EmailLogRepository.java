package com.placify.notification.repository;

import com.placify.notification.domain.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    @Modifying
    @Query("delete from EmailLog e where e.createdAt < :before")
    int deleteBefore(@Param("before") LocalDateTime before);
}
