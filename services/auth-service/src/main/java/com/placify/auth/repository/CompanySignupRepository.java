package com.placify.auth.repository;

import com.placify.auth.domain.CompanySignup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySignupRepository extends JpaRepository<CompanySignup, String> {
}
