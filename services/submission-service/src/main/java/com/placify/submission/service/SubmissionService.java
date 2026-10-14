package com.placify.submission.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.submission.client.Judge0Client;
import com.placify.submission.client.SubmissionClients;
import com.placify.submission.config.Judge0Properties;
import com.placify.submission.domain.Language;
import com.placify.submission.domain.Submission;
import com.placify.submission.domain.SubmissionResult;
import com.placify.submission.domain.SubmissionToken;
import com.placify.submission.repository.SubmissionRepository;
import com.placify.submission.repository.SubmissionResultRepository;
import com.placify.submission.repository.SubmissionTokenRepository;
import com.placify.submission.web.SubmissionDtos.ResultView;
import com.placify.submission.web.SubmissionDtos.RunRequest;
import com.placify.submission.web.SubmissionDtos.RunResponse;
import com.placify.submission.web.SubmissionDtos.SubmissionView;
import com.placify.submission.web.SubmissionDtos.SubmitRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Grading engine: one Judge0 job per test case, results collected by
 * callback (when Judge0 can reach us) and by a polling scheduler as backup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final SubmissionResultRepository results;
    private final SubmissionTokenRepository tokens;
    private final Judge0Client judge0;
    private final SubmissionClients clients;
    private final Judge0Properties props;
    private final EventPublisher publisher;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    // ---------------------------------------------------------------- submit

    @Transactional
    public SubmissionView submit(SubmitRequest req) {
        UserPrincipal me = CurrentUser.get();
        Language language = Language.byJudge0Id(req.languageId())
                .orElseThrow(() -> ApiException.badRequest("Unsupported language"));
        Submission.Mode mode = req.mode() == null ? Submission.Mode.PRACTICE : req.mode();
        Long testId = authorize(me, req, mode);

        List<SubmissionClients.TestcaseDto> cases = clients.testcases(req.questionId());
        if (cases.isEmpty()) {
            throw ApiException.badRequest("This question has no test cases yet");
        }
        byte[] secret = new byte[16];
        random.nextBytes(secret);
        Submission sub = submissions.save(Submission.builder()
                .userId(me.userId())
                .questionId(req.questionId())
                .languageId(language.judge0Id())
                .sourceCode(req.sourceCode())
                .status(Submission.Status.running)
                .mode(mode)
                .contestId(mode == Submission.Mode.CONTEST ? req.contestId() : null)
                .attemptId(mode == Submission.Mode.ASSESSMENT ? req.attemptId() : null)
                .testId(testId)
                .totalTestcases(cases.size())
                .callbackSecret(HexFormat.of().formatHex(secret))
                .build());

        LocalDateTime expires = LocalDateTime.now().plusMinutes(10);
        int queued = 0;
        for (SubmissionClients.TestcaseDto tc : cases) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("language_id", language.judge0Id());
            payload.put("source_code", req.sourceCode());
            payload.put("stdin", tc.input() == null ? "" : tc.input());
            payload.put("expected_output", tc.expectedOutput() == null ? "" : tc.expectedOutput());
            if (props.callbacksEnabled()) {
                payload.put("callback_url", props.callbackBaseUrl().replaceAll("/$", "")
                        + "/api/submissions/callback?submissionId=" + sub.getId() + "&testCaseId=" + tc.id()
                        + "&key=" + sub.getCallbackSecret());
            }
            try {
                String token = judge0.create(payload);
                tokens.save(SubmissionToken.builder().token(token).submissionId(sub.getId()).testcaseId(tc.id())
                        .hidden(tc.hidden()).status(SubmissionToken.Status.pending).expiresAt(expires).build());
                queued++;
            } catch (Exception ex) {
                log.error("Judge0 rejected test case {} of submission {}: {}", tc.id(), sub.getId(), ex.getMessage());
                saveResult(sub.getId(), tc.id(), tc.hidden(), SubmissionResult.Verdict.runtime_error, null, null, null,
                        "Code runner unavailable");
            }
        }
        if (queued == 0) {
            throw ApiException.unavailable("The code runner is not reachable right now. Please try again.");
        }
        return view(sub, true);
    }

    /** Checks that the user may submit in this mode; returns the assessment test id when relevant. */
    private Long authorize(UserPrincipal me, SubmitRequest req, Submission.Mode mode) {
        SubmissionClients.QuestionDetail q = clients.question(req.questionId());
        if (!q.active()) {
            throw ApiException.notFound("Question not found");
        }
        switch (mode) {
            case PRACTICE -> {
                if (me.role() == Role.STUDENT && !"PRACTICE".equals(q.visibility())) {
                    throw ApiException.forbidden("This problem is not available for practice");
                }
                return null;
            }
            case CONTEST -> {
                if (req.contestId() == null || !clients.contestAllows(req.contestId(), me.userId(), req.questionId())) {
                    throw ApiException.forbidden("Join the running contest to submit this problem");
                }
                return null;
            }
            case ASSESSMENT -> {
                if (req.attemptId() == null) {
                    throw ApiException.badRequest("attemptId is required for test submissions");
                }
                SubmissionClients.AttemptCheck check = clients.attemptCheck(req.attemptId(), me.userId(), req.questionId());
                if (check == null || !check.allowed()) {
                    throw ApiException.forbidden(check == null ? "Test attempt not found" : check.reason());
                }
                return check.testId();
            }
            default -> throw ApiException.badRequest("Unknown mode");
        }
    }

    // ---------------------------------------------------------------- run

    public RunResponse run(RunRequest req) {
        Language language = Language.byJudge0Id(req.languageId())
                .orElseThrow(() -> ApiException.badRequest("Unsupported language"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("language_id", language.judge0Id());
        payload.put("source_code", req.sourceCode());
        payload.put("stdin", req.stdin() == null ? "" : req.stdin());
        Map<String, Object> r;
        try {
            r = judge0.runAndWait(payload);
        } catch (Exception ex) {
            throw ApiException.unavailable("The code runner is not reachable right now");
        }
        if (r == null) {
            return new RunResponse("Timed out waiting for the runner", null, null, null, null, null, null, null);
        }
        return new RunResponse(Judge0Client.statusDescription(r), Judge0Client.statusId(r), str(r.get("stdout")),
                str(r.get("stderr")), str(r.get("compile_output")), str(r.get("message")), str(r.get("time")),
                str(r.get("memory")));
    }

    // ---------------------------------------------------------------- results (callback + polling)

    @Transactional
    public void handleCallback(String submissionId, String testcaseId, String key, Map<String, Object> body) {
        Submission sub = submissions.findById(submissionId).orElse(null);
        if (sub == null || sub.getCallbackSecret() == null || !sub.getCallbackSecret().equals(key)) {
            log.warn("Rejected Judge0 callback with a wrong key for submission {}", submissionId);
            return;
        }
        tokens.findFirstBySubmissionIdAndTestcaseId(submissionId, testcaseId).ifPresent(t -> complete(t, body));
    }

    /** Applies a finished Judge0 result for one token. */
    @Transactional
    public void complete(SubmissionToken token, Map<String, Object> body) {
        Integer statusId = Judge0Client.statusId(body);
        if (statusId == null || statusId <= 2) {
            token.setStatus(SubmissionToken.Status.processing);
            tokens.save(token);
            return;
        }
        token.setStatus(SubmissionToken.Status.completed);
        tokens.save(token);
        String error = firstNonBlank(str(body.get("compile_output")), str(body.get("stderr")), str(body.get("message")));
        saveResult(token.getSubmissionId(), token.getTestcaseId(), token.isHidden(), verdict(statusId),
                decimal(body.get("time")), decimal(body.get("memory")), str(body.get("stdout")), error);
        if (statusId == 6) {
            submissions.findById(token.getSubmissionId()).ifPresent(s -> s.setCompileOutput(error));
        }
        aggregate(token.getSubmissionId());
    }

    /** Marks tokens that never finished as errors so the submission can complete. */
    @Transactional
    public void expire(SubmissionToken token) {
        token.setStatus(SubmissionToken.Status.completed);
        tokens.save(token);
        saveResult(token.getSubmissionId(), token.getTestcaseId(), token.isHidden(), SubmissionResult.Verdict.runtime_error,
                null, null, null, "The code runner did not respond in time");
        aggregate(token.getSubmissionId());
    }

    private void saveResult(String submissionId, String testcaseId, boolean hidden, SubmissionResult.Verdict verdict,
                            BigDecimal time, BigDecimal memory, String output, String error) {
        if (results.findFirstBySubmissionIdAndTestcaseId(submissionId, testcaseId).isPresent()) {
            return; // callback and poller both delivered the same result
        }
        try {
            results.saveAndFlush(SubmissionResult.builder().submissionId(submissionId).testcaseId(testcaseId).hidden(hidden)
                    .status(verdict).executionTime(time).memoryUsed(memory)
                    .output(truncate(output, 4000)).errorMessage(truncate(error, 4000)).build());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Duplicate result ignored for {} / {}", submissionId, testcaseId);
        }
    }

    /** Recomputes passed/failed and the final verdict once every test case has a result. */
    private void aggregate(String submissionId) {
        Submission sub = submissions.findById(submissionId).orElse(null);
        if (sub == null) {
            return;
        }
        List<SubmissionResult> list = results.findBySubmissionIdOrderByIdAsc(submissionId);
        int passed = (int) list.stream().filter(r -> r.getStatus() == SubmissionResult.Verdict.accepted).count();
        sub.setTestcasesPassed(passed);
        sub.setTestcasesFailed(list.size() - passed);
        sub.setExecutionTime(list.stream().map(SubmissionResult::getExecutionTime).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        sub.setMemoryUsed(list.stream().map(SubmissionResult::getMemoryUsed).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        if (list.size() < sub.getTotalTestcases()) {
            sub.setStatus(Submission.Status.running);
            return;
        }
        sub.setScore(sub.getTotalTestcases() == 0 ? 0 : (int) Math.round(100.0 * passed / sub.getTotalTestcases()));
        sub.setStatus(finalStatus(list, passed, sub.getTotalTestcases()));
        if (!sub.isEventPublished()) {
            sub.setEventPublished(true);
            publisher.publish(RoutingKeys.SUBMISSION_JUDGED, new Events.SubmissionJudgedEvent(sub.getId(), sub.getUserId(),
                    sub.getQuestionId(), sub.getMode().name(), sub.getContestId(), sub.getAttemptId(), sub.getStatus().name(),
                    passed, sub.getTotalTestcases(), sub.getLanguageId()));
        }
    }

    static Submission.Status finalStatus(List<SubmissionResult> list, int passed, int total) {
        if (passed == total) {
            return Submission.Status.accepted;
        }
        if (list.stream().anyMatch(r -> r.getStatus() == SubmissionResult.Verdict.compilation_error)) {
            return Submission.Status.compilation_error;
        }
        if (list.stream().anyMatch(r -> r.getStatus() == SubmissionResult.Verdict.time_limit_exceeded)) {
            return Submission.Status.time_limit_exceeded;
        }
        if (list.stream().anyMatch(r -> r.getStatus() == SubmissionResult.Verdict.memory_limit_exceeded)) {
            return Submission.Status.memory_limit_exceeded;
        }
        if (list.stream().anyMatch(r -> r.getStatus() == SubmissionResult.Verdict.runtime_error)) {
            return Submission.Status.runtime_error;
        }
        return Submission.Status.wrong_answer;
    }

    /** Judge0 CE status ids: 3 accepted, 4 wrong answer, 5 TLE, 6 compile error, 7-12 runtime errors. */
    static SubmissionResult.Verdict verdict(int statusId) {
        return switch (statusId) {
            case 3 -> SubmissionResult.Verdict.accepted;
            case 4 -> SubmissionResult.Verdict.wrong_answer;
            case 5 -> SubmissionResult.Verdict.time_limit_exceeded;
            case 6 -> SubmissionResult.Verdict.compilation_error;
            default -> SubmissionResult.Verdict.runtime_error;
        };
    }

    // ---------------------------------------------------------------- reads

    public Submission getVisible(String id) {
        Submission s = submissions.findById(id).orElseThrow(() -> ApiException.notFound("Submission not found"));
        UserPrincipal me = CurrentUser.get();
        if (!me.isStaff() && !me.userId().equals(s.getUserId())) {
            if (me.role() == Role.COMPANY && s.getTestId() != null && me.userId().equals(clients.testOwner(s.getTestId()))) {
                return s;
            }
            throw ApiException.forbidden("Not your submission");
        }
        return s;
    }

    public List<SubmissionView> mine(String questionId) {
        String me = CurrentUser.id();
        List<Submission> list = questionId == null
                ? submissions.findByUserIdOrderBySubmissionTimeDesc(me, PageRequest.of(0, 50))
                : submissions.findByUserIdAndQuestionIdOrderBySubmissionTimeDesc(me, questionId);
        return list.stream().map(s -> view(s, false)).toList();
    }

    public List<ResultView> resultsFor(Submission s) {
        UserPrincipal me = CurrentUser.get();
        boolean mask = me.role() == Role.STUDENT;
        return results.findBySubmissionIdOrderByIdAsc(s.getId()).stream()
                .map(r -> new ResultView(r.getId(), r.getTestcaseId(), r.isHidden(), r.getStatus(), r.getExecutionTime(),
                        r.getMemoryUsed(), mask && r.isHidden() ? null : r.getOutput(),
                        mask && r.isHidden() ? null : r.getErrorMessage()))
                .toList();
    }

    public SubmissionView view(Submission s, boolean withCode) {
        String lang = Language.byJudge0Id(s.getLanguageId()).map(this::languageLabel).orElse("Unknown");
        return new SubmissionView(s.getId(), s.getQuestionId(), s.getUserId(), s.getLanguageId(), lang, s.getStatus(),
                s.getMode(), s.getTestcasesPassed(), s.getTestcasesFailed(), s.getTotalTestcases(), s.getExecutionTime(),
                s.getMemoryUsed(), s.getScore(), s.getCompileOutput(), s.getSubmissionTime(),
                withCode ? s.getSourceCode() : null);
    }

    // ---------------------------------------------------------------- AI review

    /** AI code review (complexity, readability, edge cases) of an accepted solution; cached. */
    @Transactional
    public Object review(String submissionId) {
        Submission s = getVisible(submissionId);
        if (!s.getUserId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("You can review only your own submissions");
        }
        if (s.getStatus() != Submission.Status.accepted) {
            throw ApiException.badRequest("AI review is available after your solution is accepted");
        }
        if (s.getMode() == Submission.Mode.ASSESSMENT) {
            throw ApiException.badRequest("AI review is not available for test submissions");
        }
        try {
            if (s.getAiReview() != null) {
                return mapper.readTree(s.getAiReview());
            }
            SubmissionClients.QuestionDetail q = clients.question(s.getQuestionId());
            String lang = Language.byJudge0Id(s.getLanguageId()).map(this::languageLabel).orElse("code");
            SubmissionClients.CodeReview review = clients.review(q, s.getSourceCode(), lang);
            s.setAiReview(mapper.writeValueAsString(review));
            return review;
        } catch (JsonProcessingException ex) {
            throw ApiException.unavailable("Could not read the AI review");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.unavailable("AI review is not available right now");
        }
    }

    // ---------------------------------------------------------------- helpers

    private String languageLabel(Language l) {
        return judge0.isLocal() ? l.localLabel() : l.label();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
