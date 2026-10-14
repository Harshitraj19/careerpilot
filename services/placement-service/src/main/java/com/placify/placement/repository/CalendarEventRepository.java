package com.placify.placement.repository;

import com.placify.placement.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime from, LocalDateTime to);

    List<CalendarEvent> findByJobIdAndType(Long jobId, CalendarEvent.Type type);

    List<CalendarEvent> findByJobIdOrderByStartTimeAsc(Long jobId);

    List<CalendarEvent> findByReminderSentFalseAndStartTimeBetween(LocalDateTime from, LocalDateTime to);
}
