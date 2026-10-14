package com.placify.submission.repository;

import com.placify.submission.domain.PlagiarismCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlagiarismCheckRepository extends JpaRepository<PlagiarismCheck, Long> {

    List<PlagiarismCheck> findByScopeAndScopeIdOrderByCreatedAtDesc(PlagiarismCheck.Scope scope, Long scopeId);
}
