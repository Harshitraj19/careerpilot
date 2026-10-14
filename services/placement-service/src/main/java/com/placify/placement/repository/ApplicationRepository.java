package com.placify.placement.repository;

import com.placify.placement.domain.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByJobIdAndStudentUserId(Long jobId, String studentUserId);

    boolean existsByJobIdAndStudentUserId(Long jobId, String studentUserId);

    List<Application> findByStudentUserIdOrderByAppliedAtDesc(String studentUserId);

    List<Application> findByJobId(Long jobId);

    List<Application> findByJobIdAndStatus(Long jobId, Application.Status status);

    List<Application> findByJobIdIn(Collection<Long> jobIds);

    List<Application> findByIdIn(Collection<Long> ids);

    long countByStudentUserIdAndStatusIn(String studentUserId, Collection<Application.Status> statuses);

    long countByJobId(Long jobId);

    @Query("""
            select count(a) from Application a, Job j
            where a.jobId = j.id and j.companyUserId = :companyUserId and a.studentUserId = :studentUserId
            """)
    long countCompanyApplicant(@Param("companyUserId") String companyUserId,
                                @Param("studentUserId") String studentUserId);

    @Query("""
            select a from Application a
            where (:jobId is null or a.jobId = :jobId)
              and (:status is null or a.status = :status)
            """)
    Page<Application> search(@Param("jobId") Long jobId,
                             @Param("status") Application.Status status,
                             Pageable pageable);
}
