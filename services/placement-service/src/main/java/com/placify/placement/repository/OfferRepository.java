package com.placify.placement.repository;

import com.placify.placement.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByStudentUserIdOrderByCreatedAtDesc(String studentUserId);

    List<Offer> findByCompanyUserIdOrderByCreatedAtDesc(String companyUserId);

    List<Offer> findByStatusOrderByCreatedAtDesc(Offer.Status status);

    List<Offer> findAllByOrderByCreatedAtDesc();

    List<Offer> findByStudentUserIdAndStatusIn(String studentUserId, Collection<Offer.Status> statuses);

    List<Offer> findByStatusIn(Collection<Offer.Status> statuses);

    Optional<Offer> findByApplicationId(Long applicationId);

    List<Offer> findByStatusAndResponseDeadlineBefore(Offer.Status status, LocalDateTime time);

    List<Offer> findByStatusAndReminderSentFalseAndResponseDeadlineBetween(Offer.Status status, LocalDateTime from, LocalDateTime to);
}
