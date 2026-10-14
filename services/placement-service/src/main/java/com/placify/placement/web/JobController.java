package com.placify.placement.web;

import com.placify.common.security.CurrentUser;
import com.placify.placement.client.PlacementClients;
import com.placify.placement.domain.Job;
import com.placify.placement.service.EligibilityService;
import com.placify.placement.service.JobService;
import com.placify.placement.service.MatchService;
import com.placify.placement.web.PlacementDtos.EligibilityView;
import com.placify.placement.web.PlacementDtos.JobRequest;
import com.placify.placement.web.PlacementDtos.JobView;
import com.placify.placement.web.PlacementDtos.NoteRequest;
import com.placify.placement.web.PlacementDtos.PublishRequest;
import com.placify.placement.web.PlacementDtos.RoundRequest;
import com.placify.placement.web.PlacementDtos.RoundView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Tag(name = "Jobs")
@RestController
@RequestMapping("/api/placement")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final EligibilityService eligibilityService;
    private final MatchService matchService;
    private final PlacementClients clients;

    @Operation(summary = "Students: open jobs with eligibility and match %. Companies: own jobs. TPO: all jobs.")
    @GetMapping("/jobs")
    public List<JobView> list(@RequestParam(required = false) Job.Status status,
                              @RequestParam(required = false) String q) {
        return jobService.listForCurrentUser(status, q);
    }

    @GetMapping("/jobs/{id}")
    public JobView one(@PathVariable Long id) {
        return jobService.view(jobService.getVisible(id));
    }

    @Operation(summary = "Post a job (goes to the TPO for approval)")
    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('COMPANY')")
    public JobView create(@Valid @RequestBody JobRequest req) {
        return jobService.view(jobService.create(req));
    }

    @PutMapping("/jobs/{id}")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public JobView update(@PathVariable Long id, @Valid @RequestBody JobRequest req) {
        return jobService.view(jobService.update(id, req));
    }

    @Operation(summary = "Publish a job to students (TPO). Optionally override the tier.")
    @PostMapping("/jobs/{id}/publish")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public JobView publish(@PathVariable Long id, @RequestBody(required = false) PublishRequest req) {
        return jobService.view(jobService.publish(id, req == null ? null : req.tier()));
    }

    @PostMapping("/jobs/{id}/reject")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public JobView reject(@PathVariable Long id, @RequestBody(required = false) NoteRequest req) {
        return jobService.view(jobService.reject(id, req == null ? null : req.note()));
    }

    @PostMapping("/jobs/{id}/close")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public JobView close(@PathVariable Long id) {
        return jobService.view(jobService.close(id));
    }

    @Operation(summary = "Detailed eligibility check with the reason for every rule")
    @GetMapping("/jobs/{id}/eligibility")
    @PreAuthorize("hasRole('STUDENT')")
    public EligibilityView eligibility(@PathVariable Long id) {
        EligibilityService.EligibilityReport r = eligibilityService.evaluate(jobService.getVisible(id),
                clients.student(CurrentUser.id()));
        return new EligibilityView(r.eligible(), r.checks());
    }

    @Operation(summary = "Smart match %, skill gap and practice problems for this job")
    @GetMapping("/jobs/{id}/match")
    @PreAuthorize("hasRole('STUDENT')")
    public MatchService.MatchResult match(@PathVariable Long id) {
        return matchService.fullMatch(jobService.getVisible(id), clients.student(CurrentUser.id()));
    }

    // ---------------------------------------------------------------- rounds

    @GetMapping("/jobs/{id}/rounds")
    public List<RoundView> rounds(@PathVariable Long id) {
        jobService.getVisible(id);
        return jobService.rounds(id).stream().map(RoundView::of).toList();
    }

    @PostMapping("/jobs/{id}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public RoundView addRound(@PathVariable Long id, @Valid @RequestBody RoundRequest req) {
        return RoundView.of(jobService.addRound(id, req));
    }

    @PutMapping("/rounds/{roundId}")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public RoundView updateRound(@PathVariable Long roundId, @Valid @RequestBody RoundRequest req) {
        return RoundView.of(jobService.updateRound(roundId, req));
    }

    @DeleteMapping("/rounds/{roundId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public void deleteRound(@PathVariable Long roundId) {
        jobService.deleteRound(roundId);
    }
}
