package com.placify.submission.repository;

import com.placify.submission.domain.PlagiarismPair;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlagiarismPairRepository extends JpaRepository<PlagiarismPair, Long> {

    List<PlagiarismPair> findByCheckIdOrderBySimilarityDesc(Long checkId);
}
