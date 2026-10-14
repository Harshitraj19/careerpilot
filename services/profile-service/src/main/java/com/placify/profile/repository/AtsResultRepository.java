package com.placify.profile.repository;

import com.placify.profile.domain.AtsResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AtsResultRepository extends JpaRepository<AtsResult, Long> {

    Page<AtsResult> findByStudentUserIdOrderByCreatedAtDesc(String studentUserId, Pageable pageable);

    Optional<AtsResult> findFirstByStudentUserIdOrderByCreatedAtDesc(String studentUserId);

    @Query("select max(a.score) from AtsResult a where a.studentUserId = :id")
    Integer bestScore(@Param("id") String studentUserId);

    @Query("select avg(a.score) from AtsResult a where a.studentUserId = :id")
    Double averageScore(@Param("id") String studentUserId);

    long countByStudentUserId(String studentUserId);
}
