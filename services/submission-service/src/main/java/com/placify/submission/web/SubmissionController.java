package com.placify.submission.web;

import com.placify.submission.client.Judge0Client;
import com.placify.submission.client.LocalCodeRunner;
import com.placify.submission.domain.Language;
import com.placify.submission.domain.PlagiarismCheck;
import com.placify.submission.plagiarism.PlagiarismService;
import com.placify.submission.service.SubmissionService;
import com.placify.submission.web.SubmissionDtos.LanguageView;
import com.placify.submission.web.SubmissionDtos.PairDetail;
import com.placify.submission.web.SubmissionDtos.PlagiarismCheckView;
import com.placify.submission.web.SubmissionDtos.PlagiarismRequest;
import com.placify.submission.web.SubmissionDtos.ResultView;
import com.placify.submission.web.SubmissionDtos.RunRequest;
import com.placify.submission.web.SubmissionDtos.RunResponse;
import com.placify.submission.web.SubmissionDtos.SubmissionView;
import com.placify.submission.web.SubmissionDtos.SubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Stream;

@Tag(name = "Code submissions")
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService service;
    private final PlagiarismService plagiarism;
    private final Judge0Client judge0;
    private final LocalCodeRunner localRunner;

    @GetMapping("/languages")
    public List<LanguageView> languages() {
        return Stream.of(Language.JAVA, Language.PYTHON, Language.CPP, Language.C, Language.JAVASCRIPT, Language.GO, Language.RUST)
                .filter(l -> !judge0.isLocal() || localRunner.supports(l))
                .map(l -> new LanguageView(l.judge0Id(), judge0.isLocal() ? l.localLabel() : l.label(), l.name())).toList();
    }

    @Operation(summary = "Run code with custom input (not graded)")
    @PostMapping("/run")
    public RunResponse run(@Valid @RequestBody RunRequest req) {
        return service.run(req);
    }

    @Operation(summary = "Submit code; it is judged against all public and hidden test cases")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmissionView submit(@Valid @RequestBody SubmitRequest req) {
        return service.submit(req);
    }

    @GetMapping("/me")
    public List<SubmissionView> mine(@RequestParam(required = false) String questionId) {
        return service.mine(questionId);
    }

    @GetMapping("/{id}")
    public SubmissionView one(@PathVariable String id) {
        return service.view(service.getVisible(id), true);
    }

    @GetMapping("/{id}/results")
    public List<ResultView> results(@PathVariable String id) {
        return service.resultsFor(service.getVisible(id));
    }

    @Operation(summary = "AI code review of an accepted solution (complexity, readability, edge cases)")
    @PostMapping("/{id}/review")
    public Object review(@PathVariable String id) {
        return service.review(id);
    }

    /** Judge0 result callback. Public, but verified with the per-submission secret key. */
    @PutMapping("/callback")
    public void callbackPut(@RequestParam String submissionId, @RequestParam String testCaseId,
                            @RequestParam String key, @RequestBody Map<String, Object> body) {
        service.handleCallback(submissionId, testCaseId, key, body);
    }

    @PostMapping("/callback")
    public void callbackPost(@RequestParam String submissionId, @RequestParam String testCaseId,
                             @RequestParam String key, @RequestBody Map<String, Object> body) {
        service.handleCallback(submissionId, testCaseId, key, body);
    }

    // ---------------------------------------------------------------- plagiarism

    @Operation(summary = "Run a plagiarism check over a test or contest")
    @PostMapping("/plagiarism/checks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public PlagiarismCheckView runCheck(@Valid @RequestBody PlagiarismRequest req) {
        return plagiarism.run(req);
    }

    @GetMapping("/plagiarism/checks")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public List<PlagiarismCheckView> checks(@RequestParam PlagiarismCheck.Scope scope, @RequestParam Long scopeId) {
        return plagiarism.list(scope, scopeId);
    }

    @GetMapping("/plagiarism/checks/{id}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public PlagiarismCheckView check(@PathVariable Long id) {
        return plagiarism.get(id);
    }

    @Operation(summary = "Both solutions of a flagged pair, side by side")
    @GetMapping("/plagiarism/pairs/{id}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public PairDetail pair(@PathVariable Long id) {
        return plagiarism.pair(id);
    }
}
