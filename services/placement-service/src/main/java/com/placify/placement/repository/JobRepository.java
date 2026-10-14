package com.placify.placement.repository;

import com.placify.placement.domain.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByCompanyUserIdOrderByCreatedAtDesc(String companyUserId);

    List<Job> findByStatusOrderByPublishedAtDesc(Job.Status status);

    List<Job> findByIdIn(Collection<Long> ids);

    List<Job> findByCompanyNameIgnoreCase(String companyName);

    @Query("""
            select j from Job j
            where (:status is null or j.status = :status)
              and (:q is null or lower(j.title) like lower(concat('%', :q, '%'))
                   or lower(j.companyName) like lower(concat('%', :q, '%')))
            """)
    Page<Job> search(@Param("status") Job.Status status, @Param("q") String q, Pageable pageable);

    long countByStatus(Job.Status status);
}
