package com.placify.assessment.web;

import com.placify.assessment.domain.Attempt;
import com.placify.assessment.domain.Test;
import com.placify.assessment.domain.TestPoolItem;
import com.placify.assessment.repository.AttemptRepository;
import com.placify.assessment.repository.TestPoolItemRepository;
import com.placify.assessment.repository.TestRepository;
import com.placify.assessment.service.AttemptService;
import com.placify.assessment.service.McqService;
import com.placify.assessment.web.AssessmentDtos.TopicStat;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
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
 * Service-to-service endpoints (submission, proctoring, interview prep hub, analytics).
 */
@RestController
@RequestMapping("/internal/assessment")
@RequiredArgsConstructor
public class InternalAssessmentController {

    private final AttemptService attempts;
    private final McqService mcqService;
    private final AttemptRepository attemptRepository;
    private final TestRepository tests;
    private final TestPoolItemRepository pool;

    public record UserAssessmentStats(String userId, List<TopicStat> practice, double practiceAccuracy,
                                      int testsTaken, Double averageTestPercent) {
    }

    public record TestIds(List<Long> ids) {
    }

    @GetMapping("/attempts/{id}/can-submit")
    public Map<String, Object> canSubmit(@PathVariable Long id, @RequestParam String userId, @RequestParam String questionId) {
        return attempts.canSubmit(id, userId, questionId);
    }

    @GetMapping("/attempts/{id}/proctoring-settings")
    public Map<String, Object> proctoring(@PathVariable Long id) {
        return attempts.proctoringSettings(id);
    }

    @GetMapping("/tests/{id}/owner")
    public Map<String, String> owner(@PathVariable Long id) {
        Test t = tests.findById(id).orElseThrow(() -> ApiException.notFound("Test not found"));
        return Map.of("ownerUserId", t.getOwnerUserId());
    }

    @GetMapping("/users/{userId}/stats")
    public UserAssessmentStats stats(@PathVariable String userId) {
        List<TopicStat> practice = mcqService.practiceStats(userId);
        int attempted = practice.stream().mapToInt(TopicStat::attempted).sum();
        int correct = practice.stream().mapToInt(TopicStat::correct).sum();
        List<Attempt> done = attemptRepository.findByStudentUserIdOrderByStartedAtDesc(userId).stream()
                .filter(a -> a.getStatus().isFinished() && a.getPercent() != null).toList();
        Double avg = done.isEmpty() ? null : done.stream().mapToDouble(a -> a.getPercent().doubleValue()).average().orElse(0);
        return new UserAssessmentStats(userId, practice, attempted == 0 ? 0 : 100.0 * correct / attempted, done.size(), avg);
    }

    /** Coding question ids used in the given tests (for the company prep hub). */
    @PostMapping("/tests/coding-questions")
    public List<String> codingQuestions(@RequestBody TestIds req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return List.of();
        }
        return pool.findByTestIdIn(req.ids()).stream()
                .filter(i -> i.getKind() == TestPoolItem.Kind.CODING)
                .map(TestPoolItem::getRefId).distinct().toList();
    }
}
