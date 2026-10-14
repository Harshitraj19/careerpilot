package com.placify.profile.web;

import com.placify.common.security.CurrentUser;
import com.placify.common.storage.FileStorageService;
import com.placify.common.web.ApiException;
import com.placify.profile.domain.StudentProfile;
import com.placify.profile.repository.StudentProfileRepository;
import com.placify.profile.service.AiClient;
import com.placify.profile.service.DocumentService;
import com.placify.profile.service.StudentProfileService;
import com.placify.profile.web.ProfileDtos.AcademicUpdate;
import com.placify.profile.web.ProfileDtos.Completeness;
import com.placify.profile.web.ProfileDtos.StudentProfileUpdate;
import com.placify.profile.web.ProfileDtos.StudentProfileView;
import com.placify.profile.web.ProfileDtos.StudentSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Student profile endpoints.
 */
@Tag(name = "Student profile")
@RestController
@RequestMapping("/api/profile/students")
@RequiredArgsConstructor
public class StudentProfileController {

    private final StudentProfileService service;
    private final StudentProfileRepository repository;
    private final FileStorageService storage;
    private final DocumentService documents;

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileView me() {
        return service.view(service.get(CurrentUser.id()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileView update(@Valid @RequestBody StudentProfileUpdate req) {
        return service.view(service.update(CurrentUser.id(), req));
    }

    @GetMapping("/me/completeness")
    @PreAuthorize("hasRole('STUDENT')")
    public Completeness completeness() {
        return service.completeness(service.get(CurrentUser.id()));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public void uploadPhoto(@RequestPart("file") MultipartFile file) {
        service.uploadPhoto(CurrentUser.id(), file);
    }

    @GetMapping("/{userId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String userId) {
        StudentProfile p = "me".equals(userId) ? service.get(CurrentUser.id()) : service.getForViewer(userId);
        if (p.getPhotoPath() == null) {
            throw ApiException.notFound("No photo");
        }
        byte[] bytes = storage.read(p.getPhotoPath());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(documents.detect(bytes))).body(bytes);
    }

    @Operation(summary = "Upload resume (PDF, max 5 MB). Text is extracted for ATS and matching.")
    @PostMapping(value = "/me/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProfileView uploadResume(@RequestPart("file") MultipartFile file) {
        return service.view(service.uploadResume(CurrentUser.id(), file));
    }

    @DeleteMapping("/me/resume")
    @PreAuthorize("hasRole('STUDENT')")
    public void deleteResume() {
        service.deleteResume(CurrentUser.id());
    }

    @GetMapping("/{userId}/resume")
    public ResponseEntity<byte[]> resume(@PathVariable String userId) {
        StudentProfile p = "me".equals(userId) ? service.get(CurrentUser.id()) : service.getForViewer(userId);
        if (p.getResumePath() == null) {
            throw ApiException.notFound("No resume uploaded");
        }
        boolean docx = p.getResumePath().endsWith(".docx");
        String filename = (p.getRegNo() + "_resume." + (docx ? "docx" : "pdf")).replaceAll("[^A-Za-z0-9_.-]", "_");
        return ResponseEntity.ok()
                .contentType(docx ? MediaType.parseMediaType(DocumentService.DOCX) : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(storage.read(p.getResumePath()));
    }

    @Operation(summary = "AI resume parsing. apply=true fills the profile from the resume.")
    @PostMapping("/me/resume/parse")
    @PreAuthorize("hasRole('STUDENT')")
    public AiClient.ParsedResume parse(@RequestParam(defaultValue = "false") boolean apply) {
        return service.parseResume(CurrentUser.id(), apply);
    }

    @Operation(summary = "View a student profile (TPO/Admin, or a company the student applied to)")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY','STUDENT')")
    public StudentProfileView one(@PathVariable String userId) {
        return service.view(service.getForViewer(userId));
    }

    @Operation(summary = "Search students (TPO/Admin)")
    @GetMapping
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public Page<StudentSummary> search(@RequestParam(required = false) String branch,
                                       @RequestParam(required = false) Integer batch,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return repository.search(blankToNull(branch), batch, blankToNull(q),
                        PageRequest.of(page, Math.min(size, 100), Sort.by("regNo")))
                .map(service::summary);
    }

    @GetMapping("/branches")
    public List<String> branches() {
        return repository.branches();
    }

    @Operation(summary = "Update academic data: branch, batch, CGPA, backlogs (TPO/Admin)")
    @PutMapping("/{userId}/academic")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public StudentProfileView academic(@PathVariable String userId, @Valid @RequestBody AcademicUpdate req) {
        return service.view(service.updateAcademic(userId, req));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
