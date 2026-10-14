package com.placify.placement.web;

import com.placify.placement.domain.Application;
import com.placify.placement.service.ApplicationService;
import com.placify.placement.service.RoundProgressionService;
import com.placify.placement.web.PlacementDtos.ApplicantView;
import com.placify.placement.web.PlacementDtos.ApplicationView;
import com.placify.placement.web.PlacementDtos.IdsRequest;
import com.placify.placement.web.PlacementDtos.LinkTestRequest;
import com.placify.placement.web.PlacementDtos.RejectRequest;
import com.placify.placement.web.PlacementDtos.ResultUpdate;
import com.placify.placement.web.PlacementDtos.RoundResultView;
import com.placify.placement.web.PlacementDtos.RoundView;
import com.placify.placement.web.PlacementDtos.Timeline;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Applications and selection rounds")
@RestController
@RequestMapping("/api/placement")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applications;
    private final RoundProgressionService progression;

    @Operation(summary = "Apply to a job (all eligibility and policy rules are checked)")
    @PostMapping("/jobs/{jobId}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public ApplicationView apply(@PathVariable Long jobId) {
        return applications.apply(jobId);
    }

    @PostMapping("/applications/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    public ApplicationView withdraw(@PathVariable Long id) {
        return applications.withdraw(id);
    }

    @GetMapping("/applications/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ApplicationView> mine() {
        return applications.mine();
    }

    @Operation(summary = "Round-by-round progress of an application")
    @GetMapping("/applications/{id}/timeline")
    public Timeline timeline(@PathVariable Long id) {
        return applications.timeline(id);
    }

    @Operation(summary = "TPO: monitor all applications")
    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public Page<ApplicationView> monitor(@RequestParam(required = false) Long jobId,
                                         @RequestParam(required = false) Application.Status status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        return applications.monitor(jobId, status, PageRequest.of(page, Math.min(size, 100), Sort.by("appliedAt").descending()));
    }

    @Operation(summary = "Applicants of a job; sort=rank ranks by match, ATS and readiness scores")
    @GetMapping("/jobs/{jobId}/applicants")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public List<ApplicantView> applicants(@PathVariable Long jobId,
                                          @RequestParam(required = false) Application.Status status,
                                          @RequestParam(defaultValue = "rank") String sort) {
        return applications.applicants(jobId, status, sort);
    }

    @PostMapping("/jobs/{jobId}/shortlist")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public Map<String, Integer> shortlist(@PathVariable Long jobId, @Valid @RequestBody IdsRequest req) {
        return Map.of("shortlisted", applications.shortlist(jobId, req.applicationIds()));
    }

    @PostMapping("/jobs/{jobId}/reject-applicants")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public Map<String, Integer> reject(@PathVariable Long jobId, @Valid @RequestBody RejectRequest req) {
        return Map.of("rejected", applications.rejectApplicants(jobId, req.applicationIds(), req.reason()));
    }

    @GetMapping("/rounds/{roundId}/results")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public List<RoundResultView> results(@PathVariable Long roundId) {
        return progression.results(roundId);
    }

    @Operation(summary = "Mark candidates CLEARED / REJECTED / ABSENT / UNDER_REVIEW. Cleared candidates move to the next round automatically.")
    @PostMapping("/rounds/{roundId}/results")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public Map<String, Integer> decide(@PathVariable Long roundId, @Valid @RequestBody ResultUpdate req) {
        return Map.of("updated", progression.decide(roundId, req));
    }

    @Operation(summary = "Link an assessment test to a test round so results apply automatically")
    @PutMapping("/rounds/{roundId}/test")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public RoundView linkTest(@PathVariable Long roundId, @Valid @RequestBody LinkTestRequest req) {
        return RoundView.of(progression.linkTest(roundId, req.assessmentTestId()));
    }
}
