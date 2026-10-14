package com.placify.profile.repository;

import com.placify.profile.domain.StudentProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, String> {

    List<StudentProfile> findByUserIdIn(Collection<String> ids);

    boolean existsByRegNo(String regNo);

    @Query("""
            select s from StudentProfile s
            where (:branch is null or s.branch = :branch)
              and (:batch is null or s.batch = :batch)
              and (:q is null or lower(s.name) like lower(concat('%', :q, '%'))
                   or lower(s.regNo) like lower(concat('%', :q, '%')))
            """)
    Page<StudentProfile> search(@Param("branch") String branch,
                                @Param("batch") Integer batch,
                                @Param("q") String q,
                                Pageable pageable);

    @Query("""
            select s from StudentProfile s
            where (:branch is null or s.branch = :branch)
              and (:batch is null or s.batch = :batch)
            """)
    List<StudentProfile> findAllFiltered(@Param("branch") String branch, @Param("batch") Integer batch);

    @Query("select distinct s.branch from StudentProfile s order by s.branch")
    List<String> branches();
}
