package com.placify.profile.web;

import com.placify.common.storage.FileStorageService;
import com.placify.common.web.ApiException;
import com.placify.profile.domain.StudentProfile;
import com.placify.profile.repository.CompanyProfileRepository;
import com.placify.profile.repository.StudentProfileRepository;
import com.placify.profile.service.AtsService;
import com.placify.profile.service.CompanyProfileService;
import com.placify.profile.service.StudentProfileService;
import com.placify.profile.web.ProfileDtos.AtsStats;
import com.placify.profile.web.ProfileDtos.CompanyProfileView;
import com.placify.profile.web.ProfileDtos.IdsRequest;
import com.placify.profile.web.ProfileDtos.StudentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service-to-service endpoints used by placement, proctoring and analytics.
 */
@RestController
@RequestMapping("/internal/profile")
@RequiredArgsConstructor
public class InternalProfileController {

    private final StudentProfileRepository students;
    private final CompanyProfileRepository companies;
    private final StudentProfileService studentService;
    private final CompanyProfileService companyService;
    private final AtsService atsService;
    private final FileStorageService storage;

    @GetMapping("/students/{userId}")
    public StudentSummary student(@PathVariable String userId) {
        return studentService.summary(studentService.get(userId));
    }

    @PostMapping("/students/batch")
    public List<StudentSummary> batch(@RequestBody IdsRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return List.of();
        }
        return students.findByUserIdIn(req.ids()).stream().map(studentService::summary).toList();
    }

    @GetMapping("/students")
    public List<StudentSummary> all(@RequestParam(required = false) String branch,
                                    @RequestParam(required = false) Integer batch) {
        return students.findAllFiltered(branch, batch).stream().map(studentService::summary).toList();
    }

    /** Resume text for matching (truncated). */
    @GetMapping("/students/{userId}/resume-text")
    public Map<String, String> resumeText(@PathVariable String userId) {
        StudentProfile p = studentService.get(userId);
        String text = p.getResumeText() == null ? "" : p.getResumeText();
        return Map.of("text", text.length() > 8000 ? text.substring(0, 8000) : text);
    }

    /** Profile photo bytes for the proctoring identity check. */
    @GetMapping("/students/{userId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String userId) {
        StudentProfile p = studentService.get(userId);
        if (p.getPhotoPath() == null) {
            throw ApiException.notFound("No photo");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(storage.read(p.getPhotoPath()));
    }

    @GetMapping("/students/{userId}/ats-stats")
    public AtsStats atsStats(@PathVariable String userId) {
        return atsService.stats(userId);
    }

    @GetMapping("/companies/{userId}")
    public CompanyProfileView company(@PathVariable String userId) {
        return CompanyProfileView.of(companyService.get(userId));
    }

    @PostMapping("/companies/batch")
    public List<CompanyProfileView> companies(@RequestBody IdsRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return List.of();
        }
        return companies.findByUserIdIn(req.ids()).stream().map(CompanyProfileView::of).toList();
    }
}
