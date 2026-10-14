package com.placify.coding.repository;

import com.placify.coding.domain.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, String> {

    boolean existsByTitle(String title);


    @Query("""
            select q from Question q
            where q.active = true
              and (:visibility is null or q.visibility = :visibility)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:tag is null or lower(q.tags) like lower(concat('%', :tag, '%')))
              and (:q is null or lower(q.title) like lower(concat('%', :q, '%')))
              and (:createdBy is null or q.createdBy = :createdBy or q.visibility = com.placify.coding.domain.Question.Visibility.PRACTICE)
            """)
    Page<Question> search(@Param("visibility") Question.Visibility visibility,
                          @Param("difficulty") Question.Difficulty difficulty,
                          @Param("tag") String tag,
                          @Param("q") String q,
                          @Param("createdBy") String createdBy,
                          Pageable pageable);

    List<Question> findByIdIn(Collection<String> ids);

    List<Question> findByVisibilityAndActiveTrue(Question.Visibility visibility);

    @Query("select q.tags from Question q where q.active = true and q.visibility = com.placify.coding.domain.Question.Visibility.PRACTICE")
    List<String> allPracticeTags();
}
