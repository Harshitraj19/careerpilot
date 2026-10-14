package com.placify.coding.repository;

import com.placify.coding.domain.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    List<Contest> findAllByOrderByStartTimeDesc();

    List<Contest> findByFinalizedFalseAndEndTimeBefore(LocalDateTime time);
}
