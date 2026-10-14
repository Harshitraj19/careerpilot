package com.placify.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.ai.domain.TutorMessage;
import com.placify.ai.domain.TutorSession;
import com.placify.ai.llm.LlmGateway;
import com.placify.ai.offline.OfflineTutor;
import com.placify.ai.repository.TutorMessageRepository;
import com.placify.ai.repository.TutorSessionRepository;
import com.placify.ai.web.AiDtos.ChatItem;
import com.placify.ai.web.AiDtos.TutorAnswer;
import com.placify.ai.web.AiDtos.TutorHistory;
import com.placify.ai.web.AiDtos.TutorQuery;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import com.placify.common.security.CurrentUser;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI coding tutor: it knows the problem,
 * the student's code and the chat so far, and teaches with progressive hints
 * instead of giving away the solution. Only available for practice problems,
 * never during tests or running contests.
 */
@Service
@RequiredArgsConstructor
public class TutorService {

    private static final String SYSTEM = """
            You are a friendly, patient coding tutor on the CareerPilot placement-preparation platform.
            Help the student solve the problem by teaching, NOT by giving the full solution.

            Progressive hint policy (current hint level is given; move up one level only when the student is still stuck):
            1. Big-picture hint: the approach or data structure to consider (1-3 sentences).
            2. Strategy: break the approach into small numbered sub-tasks with a question for each.
            3. Pseudocode or a skeleton for ONE sub-task, with its time/space complexity.
            4. A small focused code snippet (at most 10 lines) for one key idea.
            Give the complete solution ONLY if the student explicitly asks for the full solution.

            Also: point out bugs in the student's code precisely (line or construct), suggest edge cases and tests,
            adapt depth to the student's level from the history, be encouraging and concise,
            and give ONE clear next action per reply.
            Return JSON: answer (markdown text for the student), hintLevel (1-4, or 5 if you gave the full solution),
            nextStep (one short sentence).
            """;

    private final TutorSessionRepository sessions;
    private final TutorMessageRepository messages;
    private final LlmGateway llm;
    private final OfflineTutor offlineTutor;
    private final InternalClient client;

    @Value("${placify.ai.tutor-history-messages:20}")
    private int historySize;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Question(String id, String title, String description, String inputFormat, String outputFormat,
                    String constraints, String sampleInput, String sampleOutput, String difficulty, String visibility,
                    String timeLimit, String memoryLimit, List<String> tags) {
    }

    @Transactional
    public TutorHistory history(String questionId) {
        TutorSession s = session(CurrentUser.id(), questionId);
        List<ChatItem> items = messages.findBySessionIdOrderByIdAsc(s.getId()).stream()
                .map(m -> new ChatItem(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt())).toList();
        return new TutorHistory(s.getId(), s.getHintLevel(), items);
    }

    @Transactional
    public TutorAnswer ask(TutorQuery req) {
        Question q = client.get(ServiceNames.CODING, "/internal/coding/questions/" + req.questionId(), Question.class);
        if (!"PRACTICE".equals(q.visibility())) {
            throw ApiException.forbidden("The AI tutor is only available for practice problems");
        }
        TutorSession s = session(CurrentUser.id(), req.questionId());

        List<TutorMessage> recent = new ArrayList<>(messages.findBySessionIdOrderByIdDesc(s.getId(), PageRequest.of(0, historySize)));
        Collections.reverse(recent);
        String history = recent.stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String user = """
                PROBLEM: %s (%s)
                %s
                Input format: %s
                Output format: %s
                Constraints: %s
                Sample input: %s
                Sample output: %s

                CURRENT HINT LEVEL: %d

                CHAT SO FAR:
                %s

                STUDENT'S CODE (%s):
                ```
                %s
                ```

                STUDENT'S QUESTION:
                %s
                """.formatted(q.title(), q.difficulty(), q.description(), nz(q.inputFormat()), nz(q.outputFormat()),
                nz(q.constraints()), nz(q.sampleInput()), nz(q.sampleOutput()), s.getHintLevel(),
                history.isBlank() ? "(none)" : history, req.language() == null ? "code" : req.language(),
                req.code() == null || req.code().isBlank() ? "(no code yet)" : req.code(), req.query());

        OfflineTutor.Problem problem = new OfflineTutor.Problem(q.title(), q.description(), q.inputFormat(), q.constraints(), q.tags());
        TutorAnswer answer = llm.withFallback(() -> llm.structured(SYSTEM, user, TutorAnswer.class),
                () -> offlineTutor.answer(problem, s.getHintLevel(), req.query(), req.code(), req.language()));
        messages.save(TutorMessage.builder().sessionId(s.getId()).role(TutorMessage.Role.USER).content(req.query()).build());
        messages.save(TutorMessage.builder().sessionId(s.getId()).role(TutorMessage.Role.ASSISTANT)
                .content(answer.answer() == null ? "" : answer.answer()).build());
        if (answer.hintLevel() != null && answer.hintLevel() > s.getHintLevel()) {
            s.setHintLevel(Math.min(5, answer.hintLevel()));
        }
        return answer;
    }

    private TutorSession session(String userId, String questionId) {
        return sessions.findByUserIdAndQuestionId(userId, questionId)
                .orElseGet(() -> sessions.save(TutorSession.builder().userId(userId).questionId(questionId).hintLevel(0).build()));
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
