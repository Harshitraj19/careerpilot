package com.placify.placement.repository;

import com.placify.placement.domain.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByVerificationCode(String code);

    Optional<Certificate> findByOfferId(Long offerId);

    List<Certificate> findByStudentUserIdOrderByIssuedAtDesc(String studentUserId);
}
