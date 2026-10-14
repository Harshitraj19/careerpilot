package com.placify.assessment.web;

import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.service.McqService;
import com.placify.assessment.web.AssessmentDtos.GenerateRequest;
import com.placify.assessment.web.AssessmentDtos.McqRequest;
import com.placify.assessment.web.AssessmentDtos.McqView;
import com.placify.assessment.web.AssessmentDtos.PracticeAnswer;
import com.placify.assessment.web.AssessmentDtos.PracticeFeedback;
import com.placify.assessment.web.AssessmentDtos.PracticeQuestion;
import com.placify.assessment.web.AssessmentDtos.TopicStat;
import com.placify.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import java.util.Arrays;
import java.util.List;

@Tag(name = "MCQ bank and aptitude practice")
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class McqController {

    private final McqService service;

    // ---------------------------------------------------------------- practice

    @GetMapping("/practice/topics")
    public List<McqQuestion.Topic> topics() {
        return Arrays.asList(McqQuestion.Topic.values());
    }

    @Operation(summary = "Random practice questions of a topic (answers are not included)")
    @GetMapping("/practice/questions")
    public List<PracticeQuestion> practice(@RequestParam McqQuestion.Topic topic,
                                           @RequestParam(required = false) McqQuestion.Difficulty difficulty,
                                           @RequestParam(defaultValue = "10") int count) {
        return service.practiceSet(topic, difficulty, count);
    }

    @Operation(summary = "Check a practice answer; returns the correct option and explanation")
    @PostMapping("/practice/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public PracticeFeedback answer(@Valid @RequestBody PracticeAnswer req) {
        return service.answer(req);
    }

    @GetMapping("/practice/stats")
    @PreAuthorize("hasRole('STUDENT')")
    public List<TopicStat> stats() {
        return service.practiceStats(CurrentUser.id());
    }

    // ---------------------------------------------------------------- bank

    @GetMapping("/mcqs")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public Page<McqView> search(@RequestParam(required = false) McqQuestion.Topic topic,
                                @RequestParam(required = false) McqQuestion.Difficulty difficulty,
                                @RequestParam(required = false) Boolean approved,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size) {
        return service.search(topic, difficulty, approved, q, PageRequest.of(page, Math.min(size, 100), Sort.by("id").descending()));
    }

    @PostMapping("/mcqs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public McqView create(@Valid @RequestBody McqRequest req) {
        return service.view(service.create(req), true);
    }

    @PutMapping("/mcqs/{id}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public McqView update(@PathVariable Long id, @Valid @RequestBody McqRequest req) {
        return service.view(service.update(id, req), true);
    }

    @DeleteMapping("/mcqs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('TPO','ADMIN','COMPANY')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/mcqs/{id}/approve")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public McqView approve(@PathVariable Long id) {
        return service.view(service.approve(id), true);
    }

    @Operation(summary = "Generate MCQs with AI (saved as unapproved drafts for review)")
    @PostMapping("/mcqs/generate")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public List<McqView> generate(@Valid @RequestBody GenerateRequest req) {
        return service.generate(req);
    }
}
