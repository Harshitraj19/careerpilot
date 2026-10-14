package com.placify.submission.web;

import com.placify.common.web.ApiException;
import com.placify.submission.domain.Submission;
import com.placify.submission.repository.SubmissionRepository;
import com.placify.submission.service.SubmissionService;
import com.placify.submission.web.SubmissionDtos.SubmissionView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service-to-service endpoints (assessment results, analytics).
 */
@RestController
@RequestMapping("/internal/submissions")
@RequiredArgsConstructor
public class InternalSubmissionController {

    private final SubmissionRepository submissions;
    private final SubmissionService service;

    @GetMapping("/{id}")
    public SubmissionView one(@PathVariable String id) {
        Submission s = submissions.findById(id).orElseThrow(() -> ApiException.notFound("Submission not found"));
        return service.view(s, true);
    }

    @GetMapping("/attempts/{attemptId}")
    public List<SubmissionView> forAttempt(@PathVariable Long attemptId) {
        return submissions.findByAttemptIdOrderBySubmissionTimeDesc(attemptId).stream().map(s -> service.view(s, false)).toList();
    }

    @GetMapping("/users/{userId}/count")
    public Map<String, Long> count(@PathVariable String userId) {
        return Map.of("submissions", submissions.countByUserId(userId));
    }
}
