package com.placify.interview.repository;

import com.placify.interview.domain.PrepSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrepSummaryRepository extends JpaRepository<PrepSummary, String> {
}
