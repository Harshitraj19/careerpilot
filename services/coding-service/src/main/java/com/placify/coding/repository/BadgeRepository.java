package com.placify.coding.repository;

import com.placify.coding.domain.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    boolean existsByUserIdAndCode(String userId, String code);

    List<Badge> findByUserIdOrderByAwardedAtDesc(String userId);

    List<Badge> findTop30ByOrderByAwardedAtDesc();
}
