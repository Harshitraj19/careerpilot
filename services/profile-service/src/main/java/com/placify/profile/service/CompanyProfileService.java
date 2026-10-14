package com.placify.profile.service;

import com.placify.common.messaging.Events;
import com.placify.common.storage.FileStorageService;
import com.placify.common.web.ApiException;
import com.placify.profile.domain.CompanyProfile;
import com.placify.profile.repository.CompanyProfileRepository;
import com.placify.profile.web.ProfileDtos.CompanyProfileUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Company profile and logo.
 */
@Service
@RequiredArgsConstructor
public class CompanyProfileService {

    private final CompanyProfileRepository repository;
    private final FileStorageService storage;
    private final DocumentService documents;

    public CompanyProfile get(String userId) {
        return repository.findById(userId).orElseThrow(() -> ApiException.notFound("Company profile not found"));
    }

    @Transactional
    public CompanyProfile update(String userId, CompanyProfileUpdate req) {
        CompanyProfile c = get(userId);
        if (req.companyName() != null && !req.companyName().isBlank()) {
            c.setCompanyName(req.companyName().trim());
        }
        if (req.about() != null) {
            c.setAbout(req.about());
        }
        if (req.industry() != null) {
            c.setIndustry(req.industry());
        }
        if (req.website() != null) {
            c.setWebsite(req.website().trim());
        }
        if (req.location() != null) {
            c.setLocation(req.location());
        }
        if (req.employeeCount() != null) {
            c.setEmployeeCount(req.employeeCount());
        }
        return c;
    }

    @Transactional
    public void uploadLogo(String userId, MultipartFile file) {
        CompanyProfile c = get(userId);
        byte[] bytes = documents.readImage(file);
        String path = storage.save("logos", bytes, documents.extensionFor(documents.detect(bytes)));
        storage.delete(c.getLogoPath());
        c.setLogoPath(path);
    }

    @Transactional
    public void onCompanyApproved(Events.CompanyApprovedEvent e) {
        CompanyProfile c = repository.findById(e.userId()).orElseGet(() -> CompanyProfile.builder().userId(e.userId()).build());
        c.setCompanyName(e.companyName());
        c.setEmail(e.email());
        if (c.getWebsite() == null) {
            c.setWebsite(e.website());
        }
        if (c.getIndustry() == null) {
            c.setIndustry(e.industry());
        }
        repository.save(c);
    }
}
