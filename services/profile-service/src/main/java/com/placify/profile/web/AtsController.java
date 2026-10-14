package com.placify.profile.web;

import com.placify.common.security.CurrentUser;
import com.placify.profile.service.AtsService;
import com.placify.profile.web.ProfileDtos.AtsRequest;
import com.placify.profile.web.ProfileDtos.AtsStats;
import com.placify.profile.web.ProfileDtos.AtsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "ATS resume scorer")
@RestController
@RequestMapping("/api/profile/ats")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class AtsController {

    private final AtsService service;

    @Operation(summary = "Score my resume against a job description (0-100) with tips; useAi adds AI feedback")
    @PostMapping("/check")
    public AtsView check(@Valid @RequestBody AtsRequest req) {
        return service.check(CurrentUser.id(), req);
    }

    @Operation(summary = "Score a resume against a job description using uploaded files (PDF, DOCX or text)")
    @PostMapping(value = "/check-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AtsView checkFiles(@RequestParam(required = false) @Size(max = 255) String jobTitle,
                              @RequestParam(required = false) @Size(max = 255) String companyName,
                              @RequestParam(required = false) @Size(max = 20000) String jobDescription,
                              @RequestParam(required = false) MultipartFile jobDescriptionFile,
                              @RequestParam(required = false) MultipartFile resumeFile,
                              @RequestParam(defaultValue = "false") boolean useAi) {
        return service.checkFiles(CurrentUser.id(), jobTitle, companyName, jobDescription, jobDescriptionFile, resumeFile, useAi);
    }

    @GetMapping("/history")
    public Page<AtsView> history(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return service.history(CurrentUser.id(), PageRequest.of(page, Math.min(size, 50)));
    }

    @GetMapping("/stats")
    public AtsStats stats() {
        return service.stats(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public AtsView one(@PathVariable Long id) {
        return service.one(CurrentUser.id(), id);
    }
}
