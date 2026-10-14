package com.placify.placement.repository;

import com.placify.placement.domain.EventAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, Long> {

    boolean existsByEventIdAndStudentUserId(Long eventId, String studentUserId);

    boolean existsByEventIdInAndStudentUserId(Collection<Long> eventIds, String studentUserId);

    List<EventAttendance> findByEventId(Long eventId);
}
