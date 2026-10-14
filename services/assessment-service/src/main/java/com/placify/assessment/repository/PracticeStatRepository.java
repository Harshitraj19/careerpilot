package com.placify.assessment.repository;

import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.domain.PracticeStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeStatRepository extends JpaRepository<PracticeStat, Long> {

    Optional<PracticeStat> findByUserIdAndTopic(String userId, McqQuestion.Topic topic);

    List<PracticeStat> findByUserId(String userId);
}
