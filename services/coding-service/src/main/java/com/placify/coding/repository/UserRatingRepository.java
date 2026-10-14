package com.placify.coding.repository;

import com.placify.coding.domain.UserRating;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRatingRepository extends JpaRepository<UserRating, String> {

    List<UserRating> findByOrderByRatingDesc(Pageable pageable);
}
