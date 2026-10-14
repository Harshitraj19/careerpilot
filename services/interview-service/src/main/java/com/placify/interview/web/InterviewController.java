package com.placify.interview.web;

import com.placify.interview.service.EvaluationService;
import com.placify.interview.service.ExperienceService;
import com.placify.interview.service.PrepHubService;
import com.placify.interview.web.InterviewDtos.CompanyCount;
import com.placify.interview.web.InterviewDtos.EvaluationRequest;
import com.placify.interview.web.InterviewDtos.EvaluationView;
import com.placify.interview.web.InterviewDtos.ExperienceRequest;
import com.placify.interview.web.InterviewDtos.ExperienceView;
import com.placify.interview.web.InterviewDtos.MyInterviewResult;
import com.placify.interview.web.InterviewDtos.PrepHub;
import com.placify.interview.web.InterviewDtos.ReviewRequest;
import com.placify.interview.web.InterviewDtos.RoundBoard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Interviews, experiences and prep hub")
@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final EvaluationService evaluations;
    private final ExperienceService experiences;
    private final PrepHubService prepHub;

    // ---------------------------------------------------------- scorecards

    @GetMapping("/rounds/{roundId}")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public RoundBoard board(@PathVariable Long roundId) {
        return evaluations.board(roundId);
    }

    @PostMapping("/evaluations")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public EvaluationView evaluate(@Valid @RequestBody EvaluationRequest req) {
        return evaluations.evaluate(req);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<MyInterviewResult> myResults() {
        return evaluations.mine();
    }

    // ---------------------------------------------------------- experiences

    @PostMapping("/experiences")
    @PreAuthorize("hasRole('STUDENT')")
    public ExperienceView submit(@Valid @RequestBody ExperienceRequest req) {
        return experiences.submit(req);
    }

    @GetMapping("/experiences")
    public Page<ExperienceView> search(@RequestParam(required = false) String company,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return experiences.search(company, q, page, size);
    }

    @GetMapping("/experiences/companies")
    public List<CompanyCount> companies() {
        return experiences.companies();
    }

    @GetMapping("/experiences/mine")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ExperienceView> mine() {
        return experiences.mine();
    }

    @GetMapping("/experiences/pending")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public List<ExperienceView> pending() {
        return experiences.pending();
    }

    @GetMapping("/experiences/{id}")
    public ExperienceView one(@PathVariable Long id) {
        return experiences.one(id);
    }

    @PostMapping("/experiences/{id}/review")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public ExperienceView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return experiences.review(id, req);
    }

    @PostMapping("/experiences/{id}/vote")
    @PreAuthorize("hasRole('STUDENT')")
    public ExperienceView vote(@PathVariable Long id) {
        return experiences.toggleVote(id);
    }

    @DeleteMapping("/experiences/{id}")
    public void delete(@PathVariable Long id) {
        experiences.delete(id);
    }

    // ---------------------------------------------------------- prep hub

    @GetMapping("/prep")
    public PrepHub prep(@RequestParam String company) {
        return prepHub.hub(company);
    }
}
