package com.placify.placement.repository;

import com.placify.placement.domain.InterviewSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, Long> {

    List<InterviewSlot> findByJobIdOrderByStartTimeAsc(Long jobId);

    List<InterviewSlot> findByJobIdAndRoundIdOrderByStartTimeAsc(Long jobId, Long roundId);

    List<InterviewSlot> findByBookedByOrderByStartTimeAsc(String studentUserId);

    /** Slots already booked by the student that overlap [start, end). */
    @Query("""
            select s from InterviewSlot s
            where s.bookedBy = :student and s.startTime < :end and s.endTime > :start
            """)
    List<InterviewSlot> findOverlapping(@Param("student") String studentUserId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    List<InterviewSlot> findByReminderSentFalseAndBookedByIsNotNullAndStartTimeBetween(LocalDateTime from, LocalDateTime to);

    boolean existsByJobIdAndRoundIdAndBookedBy(Long jobId, Long roundId, String studentUserId);
}
