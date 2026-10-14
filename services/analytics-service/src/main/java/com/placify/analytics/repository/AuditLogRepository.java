package com.placify.analytics.repository;

import com.placify.analytics.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:service is null or a.service = :service)
              and (:action is null or a.action = :action)
              and (:actor is null or a.actorUserId = :actor)
              and (:success is null or a.success = :success)
              and a.occurredAt >= :from and a.occurredAt <= :to
            order by a.occurredAt desc
            """)
    Page<AuditLog> search(@Param("service") String service, @Param("action") String action, @Param("actor") String actor,
                          @Param("success") Boolean success, @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to, Pageable pageable);

    long countByOccurredAtAfter(LocalDateTime after);

    long countBySuccessFalseAndOccurredAtAfter(LocalDateTime after);

    @Query("select a.action, count(a) from AuditLog a where a.occurredAt >= :after group by a.action order by count(a) desc")
    List<Object[]> actionCounts(@Param("after") LocalDateTime after);

    @Modifying
    @Query("delete from AuditLog a where a.occurredAt < :before")
    int deleteBefore(@Param("before") LocalDateTime before);
}
