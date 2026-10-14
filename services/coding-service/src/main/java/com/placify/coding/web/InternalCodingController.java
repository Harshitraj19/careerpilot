package com.placify.coding.web;

import com.placify.coding.domain.ContestParticipant;
import com.placify.coding.domain.Question;
import com.placify.coding.domain.UserRating;
import com.placify.coding.repository.ContestParticipantRepository;
import com.placify.coding.repository.QuestionRepository;
import com.placify.coding.repository.TestcaseRepository;
import com.placify.coding.repository.UserQuestionStatusRepository;
import com.placify.coding.service.ContestService;
import com.placify.coding.service.PracticeService;
import com.placify.coding.service.QuestionService;
import com.placify.coding.web.CodingDtos.IdsRequest;
import com.placify.coding.web.CodingDtos.QuestionSummary;
import com.placify.coding.web.CodingDtos.TestcaseView;
import com.placify.coding.web.CodingDtos.UserCodingStats;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service-to-service endpoints: full test cases for the judge (hidden included),
 * question details for the AI tutor and assessments, recommendations and stats.
 */
@RestController
@RequestMapping("/internal/coding")
@RequiredArgsConstructor
public class InternalCodingController {

    private final QuestionRepository questions;
    private final TestcaseRepository testcases;
    private final UserQuestionStatusRepository statuses;
    private final ContestParticipantRepository participants;
    private final QuestionService questionService;
    private final PracticeService practice;
    private final ContestService contests;

    public record QuestionDetail(String id, String title, String description, String inputFormat, String outputFormat,
                                 String constraints, String sampleInput, String sampleOutput, String difficulty,
                                 List<String> tags, String timeLimit, String memoryLimit, String createdBy,
                                 String visibility, boolean active) {
    }

    @GetMapping("/questions/{id}")
    public QuestionDetail question(@PathVariable String id) {
        Question q = questions.findById(id).orElseThrow(() -> ApiException.notFound("Question not found"));
        return new QuestionDetail(q.getId(), q.getTitle(), q.getDescription(), q.getInputFormat(), q.getOutputFormat(),
                q.getConstraints(), q.getSampleInput(), q.getSampleOutput(), q.getDifficulty().name(),
                Texts.toList(q.getTags()), q.getTimeLimit().toPlainString(), q.getMemoryLimit().toPlainString(), q.getCreatedBy(),
                q.getVisibility().name(), q.isActive());
    }

    /** All test cases including hidden ones - only reachable from other services. */
    @GetMapping("/questions/{id}/testcases")
    public List<TestcaseView> testcases(@PathVariable String id) {
        return testcases.findByQuestionIdOrderByCreatedAtAsc(id).stream().map(t -> TestcaseView.of(t, false)).toList();
    }

    @PostMapping("/questions/batch")
    public List<QuestionSummary> batch(@RequestBody IdsRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return List.of();
        }
        return questions.findByIdIn(req.ids()).stream().map(q -> QuestionService.summary(q, null)).toList();
    }

    /** Practice problems for the given skills/tags (used by placement skill-gap advice). */
    @GetMapping("/recommend")
    public List<QuestionSummary> recommend(@RequestParam String tags, @RequestParam(defaultValue = "5") int limit) {
        Set<String> wanted = Texts.toSkillSet(tags);
        return questions.findByVisibilityAndActiveTrue(Question.Visibility.PRACTICE).stream()
                .filter(q -> {
                    Set<String> qt = Texts.toSkillSet(q.getTags());
                    String title = q.getTitle().toLowerCase();
                    return wanted.stream().anyMatch(w -> qt.contains(w) || title.contains(w));
                })
                .sorted(Comparator.comparingInt(Question::getRating))
                .limit(Math.min(limit, 20))
                .map(q -> QuestionService.summary(q, null))
                .toList();
    }

    @GetMapping("/contests/{contestId}/can-submit")
    public boolean canSubmit(@PathVariable Long contestId, @RequestParam String userId, @RequestParam String questionId) {
        return contests.canSubmit(contestId, userId, questionId);
    }

    @GetMapping("/users/{userId}/stats")
    public UserCodingStats stats(@PathVariable String userId) {
        UserRating r = practice.rating(userId);
        List<ContestParticipant> history = participants.findByUserId(userId).stream()
                .filter(p -> p.getFinalRank() != null).toList();
        Double percentile = history.isEmpty() ? null : history.stream().mapToDouble(p -> {
            long total = Math.max(1, participants.countByContestId(p.getContestId()));
            return 100.0 * (total - p.getFinalRank() + 1) / total;
        }).average().orElse(0);
        return new UserCodingStats(userId, r.getRating(), r.getSolved(), r.getSubmissions(), r.getEasySolved(),
                r.getMediumSolved(), r.getHardSolved(), r.getContestsJoined(), percentile);
    }

    @GetMapping("/users/{userId}/solved")
    public List<String> solved(@PathVariable String userId) {
        return statuses.findByUserIdAndSolvedTrueOrderByFirstSolvedAtDesc(userId).stream()
                .map(s -> s.getQuestionId()).collect(Collectors.toList());
    }

    @GetMapping("/questions/by-author/{userId}")
    public List<QuestionSummary> byAuthor(@PathVariable String userId) {
        return questions.findAll().stream().filter(q -> userId.equals(q.getCreatedBy()) && q.isActive())
                .map(q -> QuestionService.summary(q, null)).toList();
    }
}
