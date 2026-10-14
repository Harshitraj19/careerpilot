package com.placify.interview.repository;

import com.placify.interview.domain.InterviewExperience;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewExperienceRepository extends JpaRepository<InterviewExperience, Long> {

    @Query("""
            select e from InterviewExperience e
            where e.status = com.placify.interview.domain.InterviewExperience.Status.APPROVED
              and (:companyKey is null or e.companyKey = :companyKey)
              and (:q is null or lower(e.companyName) like :q or lower(e.jobRole) like :q or lower(e.content) like :q)
            """)
    Page<InterviewExperience> searchApproved(@Param("companyKey") String companyKey, @Param("q") String q, Pageable pageable);

    List<InterviewExperience> findByStatusOrderByCreatedAtAsc(InterviewExperience.Status status);

    List<InterviewExperience> findByAuthorUserIdOrderByCreatedAtDesc(String authorUserId);

    List<InterviewExperience> findByCompanyKeyAndStatusOrderByUpvotesDescCreatedAtDesc(String companyKey,
                                                                                       InterviewExperience.Status status);

    long countByCompanyKeyAndStatus(String companyKey, InterviewExperience.Status status);

    @Query("""
            select e.companyName, count(e) from InterviewExperience e
            where e.status = com.placify.interview.domain.InterviewExperience.Status.APPROVED
            group by e.companyKey, e.companyName order by count(e) desc
            """)
    List<Object[]> companiesWithExperiences();

    @Modifying
    @Query("update InterviewExperience e set e.upvotes = e.upvotes + :delta where e.id = :id")
    int addVotes(@Param("id") Long id, @Param("delta") int delta);
}
