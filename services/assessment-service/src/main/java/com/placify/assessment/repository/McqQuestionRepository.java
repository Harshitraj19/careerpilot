package com.placify.assessment.repository;

import com.placify.assessment.domain.McqQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface McqQuestionRepository extends JpaRepository<McqQuestion, Long> {

    @Query("""
            select m from McqQuestion m
            where (:topic is null or m.topic = :topic)
              and (:difficulty is null or m.difficulty = :difficulty)
              and (:approved is null or m.approved = :approved)
              and (:q is null or lower(m.text) like lower(concat('%', :q, '%'))
                   or lower(m.subtopic) like lower(concat('%', :q, '%')))
            """)
    Page<McqQuestion> search(@Param("topic") McqQuestion.Topic topic,
                             @Param("difficulty") McqQuestion.Difficulty difficulty,
                             @Param("approved") Boolean approved,
                             @Param("q") String q,
                             Pageable pageable);

    @Query(value = """
            select * from mcq_questions
            where approved = true and topic = :topic and (:difficulty is null or difficulty = :difficulty)
            order by rand() limit :count
            """, nativeQuery = true)
    List<McqQuestion> randomApproved(@Param("topic") String topic, @Param("difficulty") String difficulty,
                                     @Param("count") int count);

    List<McqQuestion> findByIdIn(Collection<Long> ids);

    @Query("select m.text from McqQuestion m")
    List<String> allTexts();
}
