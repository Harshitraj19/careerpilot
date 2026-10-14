package com.placify.coding.repository;

import com.placify.coding.domain.Testcase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestcaseRepository extends JpaRepository<Testcase, String> {

    List<Testcase> findByQuestionIdOrderByCreatedAtAsc(String questionId);

    List<Testcase> findByQuestionIdAndHiddenFalseOrderByCreatedAtAsc(String questionId);

    long countByQuestionId(String questionId);
}
