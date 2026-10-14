package com.placify.coding.service;

import com.placify.coding.domain.Question;
import com.placify.coding.domain.Testcase;
import com.placify.coding.repository.ContestQuestionRepository;
import com.placify.coding.repository.QuestionRepository;
import com.placify.coding.repository.TestcaseRepository;
import com.placify.coding.repository.UserQuestionStatusRepository;
import com.placify.coding.web.CodingDtos.QuestionRequest;
import com.placify.coding.web.CodingDtos.QuestionSummary;
import com.placify.coding.web.CodingDtos.QuestionView;
import com.placify.coding.web.CodingDtos.TestcaseRequest;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.util.Texts;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Coding question bank and test cases.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questions;
    private final TestcaseRepository testcases;
    private final UserQuestionStatusRepository statuses;
    private final ContestQuestionRepository contestQuestions;

    public Question get(String id) {
        return questions.findById(id).filter(Question::isActive)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
    }

    /** Students may only open practice problems (contest problems are opened through the contest). */
    public Question getForViewer(String id) {
        Question q = get(id);
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.STUDENT && q.getVisibility() != Question.Visibility.PRACTICE) {
            throw ApiException.notFound("Question not found");
        }
        if (me.role() == Role.COMPANY && q.getVisibility() != Question.Visibility.PRACTICE
                && !me.userId().equals(q.getCreatedBy())) {
            throw ApiException.notFound("Question not found");
        }
        return q;
    }

    public Page<QuestionSummary> search(Question.Difficulty difficulty, String tag, String q, Pageable pageable) {
        UserPrincipal me = CurrentUser.get();
        Question.Visibility visibility = me.role() == Role.STUDENT ? Question.Visibility.PRACTICE : null;
        String createdBy = me.role() == Role.COMPANY ? me.userId() : null;
        Set<String> solved = me.role() == Role.STUDENT
                ? statuses.findByUserIdAndSolvedTrueOrderByFirstSolvedAtDesc(me.userId()).stream()
                .map(s -> s.getQuestionId()).collect(Collectors.toSet())
                : Set.of();
        return questions.search(visibility, difficulty, blank(tag), blank(q), createdBy, pageable)
                .map(x -> summary(x, me.role() == Role.STUDENT ? solved.contains(x.getId()) : null));
    }

    @Transactional
    public Question create(QuestionRequest req) {
        UserPrincipal me = CurrentUser.get();
        Question.Visibility visibility = req.visibility() == null ? Question.Visibility.PRACTICE : req.visibility();
        if (me.role() == Role.COMPANY) {
            visibility = Question.Visibility.TEST_ONLY; // company questions are for their own tests
        }
        Question q = Question.builder()
                .createdBy(me.userId())
                .visibility(visibility)
                .rating(req.difficulty().defaultRating())
                .active(true)
                .build();
        apply(q, req);
        q = questions.save(q);
        if (req.testcases() != null) {
            for (TestcaseRequest t : req.testcases()) {
                addTestcase(q.getId(), t);
            }
        }
        return q;
    }

    @Transactional
    public Question update(String id, QuestionRequest req) {
        Question q = editable(id);
        Question.Difficulty before = q.getDifficulty();
        apply(q, req);
        if (before != q.getDifficulty()) {
            q.setRating(q.getDifficulty().defaultRating());
        }
        if (req.visibility() != null && CurrentUser.get().role() != Role.COMPANY) {
            q.setVisibility(req.visibility());
        }
        return q;
    }

    @Transactional
    public void delete(String id) {
        Question q = editable(id);
        if (contestQuestions.existsByQuestionId(id)) {
            q.setActive(false); // keep history of contests intact
        } else {
            questions.delete(q);
        }
    }

    private void apply(Question q, QuestionRequest req) {
        q.setTitle(req.title().trim());
        q.setDescription(req.description());
        q.setInputFormat(req.inputFormat());
        q.setOutputFormat(req.outputFormat());
        q.setConstraints(req.constraints());
        q.setSampleInput(req.sampleInput());
        q.setSampleOutput(req.sampleOutput());
        q.setExplanation(req.explanation());
        q.setDifficulty(req.difficulty());
        q.setTags(req.tags() == null ? null : Texts.join(req.tags().stream().map(String::toLowerCase).toList()));
        q.setTimeLimit(req.timeLimit() == null ? new BigDecimal("2.00") : req.timeLimit());
        q.setMemoryLimit(req.memoryLimit() == null ? new BigDecimal("256.00") : req.memoryLimit());
    }

    public Question editable(String id) {
        Question q = get(id);
        UserPrincipal me = CurrentUser.get();
        if (!me.isStaff() && !me.userId().equals(q.getCreatedBy())) {
            throw ApiException.forbidden("Only the author or the placement office can change this question");
        }
        return q;
    }

    public boolean canEdit(Question q) {
        UserPrincipal me = CurrentUser.get();
        return me.isStaff() || me.userId().equals(q.getCreatedBy());
    }

    // ---------------------------------------------------------------- test cases

    @Transactional
    public Testcase addTestcase(String questionId, TestcaseRequest req) {
        return testcases.save(Testcase.builder()
                .questionId(questionId)
                .input(req.input())
                .expectedOutput(req.expectedOutput())
                .hidden(req.hidden() == null || req.hidden())
                .timeLimit(req.timeLimit())
                .memoryLimit(req.memoryLimit())
                .build());
    }

    @Transactional
    public Testcase updateTestcase(String id, TestcaseRequest req) {
        Testcase t = testcases.findById(id).orElseThrow(() -> ApiException.notFound("Test case not found"));
        editable(t.getQuestionId());
        t.setInput(req.input());
        t.setExpectedOutput(req.expectedOutput());
        if (req.hidden() != null) {
            t.setHidden(req.hidden());
        }
        t.setTimeLimit(req.timeLimit());
        t.setMemoryLimit(req.memoryLimit());
        return t;
    }

    @Transactional
    public void deleteTestcase(String id) {
        Testcase t = testcases.findById(id).orElseThrow(() -> ApiException.notFound("Test case not found"));
        editable(t.getQuestionId());
        testcases.delete(t);
    }

    // ---------------------------------------------------------------- mapping

    public QuestionView view(Question q, Boolean solved) {
        return new QuestionView(q.getId(), q.getTitle(), q.getDescription(), q.getInputFormat(), q.getOutputFormat(),
                q.getConstraints(), q.getSampleInput(), q.getSampleOutput(), q.getExplanation(), q.getDifficulty(),
                Texts.toList(q.getTags()), q.getRating(), q.getVisibility(), q.getTimeLimit(), q.getMemoryLimit(),
                q.getSolvedCount(), q.getAttemptCount(), solved, canEdit(q));
    }

    public static QuestionSummary summary(Question q, Boolean solved) {
        return new QuestionSummary(q.getId(), q.getTitle(), q.getDifficulty(), Texts.toList(q.getTags()), q.getRating(),
                q.getVisibility(), q.getSolvedCount(), q.getAttemptCount(), solved);
    }

    public java.util.List<String> tags() {
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (String csv : questions.allPracticeTags()) {
            Texts.toList(csv).forEach(t -> counts.merge(t.toLowerCase(), 1, Integer::sum));
        }
        return counts.keySet().stream().toList();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
