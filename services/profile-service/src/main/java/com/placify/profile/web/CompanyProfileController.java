package com.placify.profile.web;

import com.placify.common.security.CurrentUser;
import com.placify.common.storage.FileStorageService;
import com.placify.common.web.ApiException;
import com.placify.profile.domain.CompanyProfile;
import com.placify.profile.service.CompanyProfileService;
import com.placify.profile.service.DocumentService;
import com.placify.profile.web.ProfileDtos.CompanyProfileUpdate;
import com.placify.profile.web.ProfileDtos.CompanyProfileView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@Tag(name = "Company profile")
@RestController
@RequestMapping("/api/profile/companies")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final CompanyProfileService service;
    private final FileStorageService storage;
    private final DocumentService documents;

    @GetMapping("/me")
    @PreAuthorize("hasRole('COMPANY')")
    public CompanyProfileView me() {
        return CompanyProfileView.of(service.get(CurrentUser.id()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('COMPANY')")
    public CompanyProfileView update(@Valid @RequestBody CompanyProfileUpdate req) {
        return CompanyProfileView.of(service.update(CurrentUser.id(), req));
    }

    @PostMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COMPANY')")
    public void logo(@RequestPart("file") MultipartFile file) {
        service.uploadLogo(CurrentUser.id(), file);
    }

    @GetMapping("/{userId}")
    public CompanyProfileView one(@PathVariable String userId) {
        return CompanyProfileView.of(service.get(userId));
    }

    @GetMapping("/{userId}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String userId) {
        CompanyProfile c = service.get(userId);
        if (c.getLogoPath() == null) {
            throw ApiException.notFound("No logo");
        }
        byte[] bytes = storage.read(c.getLogoPath());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.parseMediaType(documents.detect(bytes)))
                .body(bytes);
    }
}
