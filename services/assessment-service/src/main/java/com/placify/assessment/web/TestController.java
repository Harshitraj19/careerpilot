package com.placify.assessment.web;

import com.placify.assessment.service.AttemptService;
import com.placify.assessment.service.TestService;
import com.placify.assessment.web.AssessmentDtos.AnswerRequest;
import com.placify.assessment.web.AssessmentDtos.AttemptResult;
import com.placify.assessment.web.AssessmentDtos.AttemptView;
import com.placify.assessment.web.AssessmentDtos.CodingStatement;
import com.placify.assessment.web.AssessmentDtos.PoolItemView;
import com.placify.assessment.web.AssessmentDtos.TestRequest;
import com.placify.assessment.web.AssessmentDtos.TestView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tests and attempts")
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class TestController {

    private final TestService tests;
    private final AttemptService attempts;

    @Operation(summary = "Students: tests available to me. Companies: my tests. TPO: all tests.")
    @GetMapping("/tests")
    public List<TestView> list() {
        return tests.listForCurrentUser();
    }

    @GetMapping("/tests/{id}")
    public TestView one(@PathVariable Long id) {
        return tests.view(tests.get(id), null);
    }

    @Operation(summary = "Create a test with MCQ/coding pools, timing, cut-off and proctoring settings")
    @PostMapping("/tests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public TestView create(@Valid @RequestBody TestRequest req) {
        return tests.view(tests.create(req), null);
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public TestView update(@PathVariable Long id, @Valid @RequestBody TestRequest req) {
        return tests.view(tests.update(id, req), null);
    }

    @DeleteMapping("/tests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public void delete(@PathVariable Long id) {
        tests.delete(id);
    }

    @PostMapping("/tests/{id}/publish")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public TestView publish(@PathVariable Long id) {
        return tests.view(tests.publish(id), null);
    }

    @PostMapping("/tests/{id}/close")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public TestView close(@PathVariable Long id) {
        return tests.view(tests.close(id), null);
    }

    @GetMapping("/tests/{id}/pool")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public List<PoolItemView> pool(@PathVariable Long id) {
        return tests.poolView(id);
    }

    @Operation(summary = "All attempts of a test with scores and proctoring trust scores")
    @GetMapping("/tests/{id}/results")
    @PreAuthorize("hasAnyRole('COMPANY','TPO','ADMIN')")
    public List<AttemptResult> results(@PathVariable Long id) {
        return attempts.resultsForTest(id);
    }

    // ---------------------------------------------------------------- student

    @Operation(summary = "Start (or resume) a test. Each student gets a random question set and option order.")
    @PostMapping("/tests/{id}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView start(@PathVariable Long id) {
        return attempts.start(id);
    }

    @GetMapping("/attempts/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptView attempt(@PathVariable Long id) {
        return attempts.get(id);
    }

    @PostMapping("/attempts/{id}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public void answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest req) {
        attempts.answer(id, req);
    }

    @GetMapping("/attempts/{id}/coding/{questionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public CodingStatement coding(@PathVariable Long id, @PathVariable String questionId) {
        return attempts.codingStatement(id, questionId);
    }

    @PostMapping("/attempts/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptResult submit(@PathVariable Long id) {
        return attempts.submit(id);
    }

    @GetMapping("/attempts/{id}/result")
    public AttemptResult result(@PathVariable Long id) {
        return attempts.resultFor(id);
    }

    @GetMapping("/attempts/me")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AttemptResult> mine() {
        return attempts.mineResults();
    }
}
