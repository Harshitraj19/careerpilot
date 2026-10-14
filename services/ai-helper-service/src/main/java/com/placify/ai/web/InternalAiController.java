package com.placify.ai.web;

import com.placify.ai.embedding.EmbeddingService;
import com.placify.ai.service.AiTasksService;
import com.placify.ai.service.MockInterviewService;
import com.placify.ai.web.AiDtos.CodeReview;
import com.placify.ai.web.AiDtos.CodeReviewRequest;
import com.placify.ai.web.AiDtos.ExperienceSummary;
import com.placify.ai.web.AiDtos.GeneratedMcq;
import com.placify.ai.web.AiDtos.McqGenerateRequest;
import com.placify.ai.web.AiDtos.MockStats;
import com.placify.ai.web.AiDtos.ParsedResume;
import com.placify.ai.web.AiDtos.ResumeFeedback;
import com.placify.ai.web.AiDtos.ResumeFeedbackRequest;
import com.placify.ai.web.AiDtos.ResumeParseRequest;
import com.placify.ai.web.AiDtos.SimilarityRequest;
import com.placify.ai.web.AiDtos.SummarizeRequest;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-to-service AI endpoints (protected by the internal secret). */
@Hidden
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final EmbeddingService embeddings;
    private final AiTasksService tasks;
    private final MockInterviewService mock;

    @PostMapping("/similarity")
    public EmbeddingService.Similarity similarity(@RequestBody SimilarityRequest req) {
        return embeddings.similarity(req.textA() == null ? "" : req.textA(), req.textB() == null ? "" : req.textB());
    }

    @PostMapping("/resume/parse")
    public ParsedResume parse(@RequestBody ResumeParseRequest req) {
        return tasks.parseResume(req.resumeText());
    }

    @PostMapping("/resume/feedback")
    public ResumeFeedback feedback(@RequestBody ResumeFeedbackRequest req) {
        return tasks.resumeFeedback(req.resumeText(), req.jobDescription());
    }

    @PostMapping("/code-review")
    public CodeReview review(@RequestBody CodeReviewRequest req) {
        return tasks.reviewCode(req);
    }

    @PostMapping("/mcq/generate")
    public List<GeneratedMcq> mcqs(@RequestBody McqGenerateRequest req) {
        return tasks.generateMcqs(req);
    }

    @PostMapping("/summarize-experiences")
    public ExperienceSummary summarize(@RequestBody SummarizeRequest req) {
        return tasks.summarizeExperiences(req);
    }

    @GetMapping("/users/{userId}/mock-stats")
    public MockStats mockStats(@PathVariable String userId) {
        return mock.stats(userId);
    }
}
