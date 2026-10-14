package com.placify.ai.web;

import com.placify.ai.llm.LlmGateway;
import com.placify.ai.service.AiTasksService;
import com.placify.ai.service.MockInterviewService;
import com.placify.ai.service.TutorService;
import com.placify.ai.web.AiDtos.AnswerRequest;
import com.placify.ai.web.AiDtos.InterviewQuestion;
import com.placify.ai.web.AiDtos.InterviewQuestionsRequest;
import com.placify.ai.web.AiDtos.InterviewView;
import com.placify.ai.web.AiDtos.StartInterview;
import com.placify.ai.web.AiDtos.TutorAnswer;
import com.placify.ai.web.AiDtos.TutorHistory;
import com.placify.ai.web.AiDtos.TutorQuery;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "AI assistant")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class AiController {

    private final TutorService tutor;
    private final MockInterviewService mock;
    private final AiTasksService tasks;
    private final LlmGateway llm;

    /** Which engine answers: a configured language model, or CareerPilot's built-in engine. */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> status() {
        return Map.of("languageModel", llm.configured(), "engine", llm.configured() ? llm.model() : "CareerPilot built-in engine");
    }

    @PostMapping("/tutor/query")
    public TutorAnswer ask(@Valid @RequestBody TutorQuery req) {
        return tutor.ask(req);
    }

    @GetMapping("/tutor/history")
    public TutorHistory history(@RequestParam String questionId) {
        return tutor.history(questionId);
    }

    @PostMapping("/mock-interviews")
    public InterviewView start(@Valid @RequestBody StartInterview req) {
        return mock.start(req);
    }

    @PostMapping("/mock-interviews/{id}/answer")
    public InterviewView answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest req) {
        return mock.answer(id, req.answer());
    }

    @GetMapping("/mock-interviews")
    public List<InterviewView> list() {
        return mock.mineList();
    }

    @GetMapping("/mock-interviews/{id}")
    public InterviewView one(@PathVariable Long id) {
        return mock.one(id);
    }

    @PostMapping("/interview-questions")
    public List<InterviewQuestion> interviewQuestions(@Valid @RequestBody InterviewQuestionsRequest req) {
        return tasks.interviewQuestions(req);
    }
}
