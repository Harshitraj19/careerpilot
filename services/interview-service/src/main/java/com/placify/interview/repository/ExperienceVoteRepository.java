package com.placify.interview.repository;

import com.placify.interview.domain.ExperienceVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExperienceVoteRepository extends JpaRepository<ExperienceVote, Long> {

    Optional<ExperienceVote> findByExperienceIdAndUserId(Long experienceId, String userId);

    List<ExperienceVote> findByUserIdAndExperienceIdIn(String userId, Collection<Long> experienceIds);
}
