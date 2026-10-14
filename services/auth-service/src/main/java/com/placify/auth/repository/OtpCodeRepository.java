package com.placify.auth.repository;

import com.placify.auth.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(String email, OtpCode.Purpose purpose);

    Optional<OtpCode> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpCode.Purpose purpose);
}
