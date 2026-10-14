package com.placify.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.ai.domain.MockInterview;
import com.placify.ai.domain.MockInterviewTurn;
import com.placify.ai.llm.LlmGateway;
import com.placify.ai.offline.OfflineInterviewer;
import com.placify.ai.repository.MockInterviewRepository;
import com.placify.ai.repository.MockInterviewTurnRepository;
import com.placify.ai.web.AiDtos.AnswerEvaluation;
import com.placify.ai.web.AiDtos.InterviewSummary;
import com.placify.ai.web.AiDtos.InterviewView;
import com.placify.ai.web.AiDtos.MockStats;
import com.placify.ai.web.AiDtos.StartInterview;
import com.placify.ai.web.AiDtos.TurnView;
import com.placify.common.security.CurrentUser;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Text-based AI mock interview: the AI asks one question at a time (adapting
 * to previous answers), scores every answer 0-10 with feedback and ends with an
 * overall score, strengths and topics to revise.
 */
@Service
@RequiredArgsConstructor
public class MockInterviewService {

    private static final String INTERVIEWER = """
            You are an experienced campus-placement interviewer for the role described. Ask exactly ONE clear
            interview question. Interview type: TECHNICAL = concepts, problem solving, projects and CS fundamentals
            (DSA, DBMS, OS, networks, OOP) relevant to the role and skills; HR = behavioural questions (STAR style);
            MIXED = alternate. Do not repeat earlier questions. Make the next question slightly harder if the last
            answer was strong and simpler if it was weak. Reply with only the question text.
            """;

    private static final String EVALUATOR = """
            You are a fair interview evaluator. Score the candidate's answer from 0 to 10 for correctness,
            depth, clarity and structure (for HR answers: STAR structure, honesty, relevance). Be specific and kind.
            Return JSON: score (0-10 integer), feedback (2-4 sentences), idealPoints (3-5 key points a strong answer covers).
            """;

    private static final String SUMMARISER = """
            You are summarising a mock interview for a student preparing for placements. Using the questions,
            answers and scores, return JSON: overallScore (0-100), strengths (2-4 items), improvements (2-4 items),
            verdict (one sentence: e.g. "Ready for most product-company interviews"), topicsToRevise (2-5 items).
            """;

    private final MockInterviewRepository interviews;
    private final MockInterviewTurnRepository turns;
    private final LlmGateway llm;
    private final OfflineInterviewer interviewer;
    private final ObjectMapper mapper;

    @Transactional
    public InterviewView start(StartInterview req) {
        String userId = CurrentUser.id();
        // close any unfinished interview so stats stay clean
        interviews.findByUserIdAndStatus(userId, MockInterview.Status.IN_PROGRESS).forEach(i -> i.setStatus(MockInterview.Status.COMPLETED));
        MockInterview interview = interviews.save(MockInterview.builder()
                .userId(userId)
                .targetRole(req.role().trim())
                .type(req.type())
                .skills(req.skills() == null ? null : Texts.join(req.skills()))
                .difficulty(req.difficulty() == null || req.difficulty().isBlank() ? "MEDIUM" : req.difficulty().toUpperCase())
                .questionCount(req.questionCount())
                .status(MockInterview.Status.IN_PROGRESS)
                .build());
        askNext(interview, List.of());
        return view(interview);
    }

    @Transactional
    public InterviewView answer(Long id, String answer) {
        MockInterview interview = mine(id);
        if (interview.getStatus() != MockInterview.Status.IN_PROGRESS) {
            throw ApiException.badRequest("This interview is already finished");
        }
        List<MockInterviewTurn> list = turns.findByInterviewIdOrderBySeqAsc(id);
        MockInterviewTurn current = list.get(list.size() - 1);
        if (current.getAnswer() != null) {
            throw ApiException.badRequest("Waiting for the next question");
        }

        AnswerEvaluation eval = llm.withFallback(() -> llm.structured(EVALUATOR, """
                ROLE: %s (%s interview, %s difficulty)
                QUESTION: %s
                CANDIDATE'S ANSWER: %s
                """.formatted(interview.getTargetRole(), interview.getType(), interview.getDifficulty(),
                current.getQuestion(), answer), AnswerEvaluation.class),
                () -> interviewer.evaluate(current.getQuestion(), answer));
        current.setAnswer(answer);
        current.setAnsweredAt(LocalDateTime.now());
        current.setScore(eval.score() == null ? 0 : Math.max(0, Math.min(10, eval.score())));
        current.setFeedback(eval.feedback());
        current.setIdealPoints(eval.idealPoints() == null ? null : String.join("\n", eval.idealPoints()));

        if (list.size() >= interview.getQuestionCount()) {
            finish(interview, list);
        } else {
            askNext(interview, list);
        }
        return view(interview);
    }

    private void askNext(MockInterview interview, List<MockInterviewTurn> previous) {
        String transcript = previous.stream()
                .map(t -> "Q" + t.getSeq() + ": " + t.getQuestion() + "\nScore: " + t.getScore())
                .collect(Collectors.joining("\n"));
        String question = llm.withFallback(() -> llm.text(INTERVIEWER, """
                ROLE: %s
                TYPE: %s
                DIFFICULTY: %s
                CANDIDATE SKILLS: %s
                QUESTION NUMBER: %d of %d
                PREVIOUS QUESTIONS AND SCORES:
                %s
                """.formatted(interview.getTargetRole(), interview.getType(), interview.getDifficulty(),
                interview.getSkills() == null ? "not specified" : interview.getSkills(), previous.size() + 1,
                interview.getQuestionCount(), transcript.isBlank() ? "(first question)" : transcript)),
                () -> interviewer.nextQuestion(interview, previous));
        turns.save(MockInterviewTurn.builder().interviewId(interview.getId()).seq(previous.size() + 1)
                .question(question == null ? "Tell me about yourself." : question.trim()).askedAt(LocalDateTime.now()).build());
    }

    private void finish(MockInterview interview, List<MockInterviewTurn> all) {
        String transcript = all.stream()
                .map(t -> "Q" + t.getSeq() + ": " + t.getQuestion() + "\nA: " + t.getAnswer() + "\nScore: " + t.getScore() + "/10")
                .collect(Collectors.joining("\n\n"));
        InterviewSummary summary = llm.withFallback(
                () -> llm.structured(SUMMARISER, "ROLE: " + interview.getTargetRole() + "\n\n" + transcript, InterviewSummary.class),
                () -> interviewer.summarise(interview, all));
        OptionalDouble avg = all.stream().filter(t -> t.getScore() != null).mapToInt(MockInterviewTurn::getScore).average();
        int computed = (int) Math.round(avg.orElse(0) * 10);
        interview.setOverallScore(summary != null && summary.overallScore() != null ? summary.overallScore() : computed);
        interview.setStatus(MockInterview.Status.COMPLETED);
        interview.setCompletedAt(LocalDateTime.now());
        try {
            interview.setSummaryJson(summary == null ? null : mapper.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            interview.setSummaryJson(null);
        }
    }

    public List<InterviewView> mineList() {
        return interviews.findByUserIdOrderByCreatedAtDesc(CurrentUser.id()).stream().map(this::view).toList();
    }

    public InterviewView one(Long id) {
        return view(mine(id));
    }

    public MockStats stats(String userId) {
        List<MockInterview> done = interviews.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(i -> i.getStatus() == MockInterview.Status.COMPLETED && i.getOverallScore() != null).toList();
        OptionalDouble avg = done.stream().mapToInt(MockInterview::getOverallScore).average();
        Integer best = done.stream().map(MockInterview::getOverallScore).max(Integer::compare).orElse(null);
        return new MockStats(userId, done.size(), avg.isPresent() ? avg.getAsDouble() : null, best);
    }

    private MockInterview mine(Long id) {
        MockInterview i = interviews.findById(id).orElseThrow(() -> ApiException.notFound("Interview not found"));
        if (!i.getUserId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("Not your interview");
        }
        return i;
    }

    private InterviewView view(MockInterview i) {
        List<MockInterviewTurn> list = turns.findByInterviewIdOrderBySeqAsc(i.getId());
        String current = list.isEmpty() || list.get(list.size() - 1).getAnswer() != null
                ? null : list.get(list.size() - 1).getQuestion();
        InterviewSummary summary = null;
        if (i.getSummaryJson() != null) {
            try {
                summary = mapper.readValue(i.getSummaryJson(), InterviewSummary.class);
            } catch (JsonProcessingException ignored) {
                // keep null
            }
        }
        List<TurnView> turnViews = list.stream()
                .map(t -> new TurnView(t.getSeq(), t.getQuestion(), t.getAnswer(), t.getScore(), t.getFeedback(),
                        t.getIdealPoints() == null ? List.of() : Arrays.asList(t.getIdealPoints().split("\n"))))
                .toList();
        int answered = (int) list.stream().filter(t -> t.getAnswer() != null).count();
        return new InterviewView(i.getId(), i.getTargetRole(), i.getType(), i.getDifficulty(), i.getQuestionCount(),
                i.getStatus(), i.getOverallScore(), summary, current, answered, turnViews, i.getCreatedAt());
    }
}
