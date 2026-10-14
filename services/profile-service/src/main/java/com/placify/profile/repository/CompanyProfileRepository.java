package com.placify.profile.repository;

import com.placify.profile.domain.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, String> {

    List<CompanyProfile> findByUserIdIn(Collection<String> ids);
}
