package com.placify.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.assessment.client.AssessmentClients;
import com.placify.assessment.domain.McqQuestion;
import com.placify.assessment.domain.PracticeStat;
import com.placify.assessment.repository.McqQuestionRepository;
import com.placify.assessment.repository.PracticeStatRepository;
import com.placify.assessment.web.AssessmentDtos.GenerateRequest;
import com.placify.assessment.web.AssessmentDtos.McqRequest;
import com.placify.assessment.web.AssessmentDtos.McqView;
import com.placify.assessment.web.AssessmentDtos.PracticeAnswer;
import com.placify.assessment.web.AssessmentDtos.PracticeFeedback;
import com.placify.assessment.web.AssessmentDtos.PracticeQuestion;
import com.placify.assessment.web.AssessmentDtos.TopicStat;
import com.placify.common.audit.Audited;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MCQ bank (manual or AI-generated, TPO-approved) and aptitude practice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McqService {

    private final McqQuestionRepository repository;
    private final PracticeStatRepository stats;
    private final AssessmentClients clients;
    private final ObjectMapper mapper;

    public McqQuestion get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Question not found"));
    }

    public Page<McqView> search(McqQuestion.Topic topic, McqQuestion.Difficulty difficulty, Boolean approved, String q,
                                Pageable pageable) {
        return repository.search(topic, difficulty, approved, q == null || q.isBlank() ? null : q.trim(), pageable)
                .map(m -> view(m, true));
    }

    @Transactional
    public McqQuestion create(McqRequest req) {
        validateOptions(req);
        UserPrincipal me = CurrentUser.get();
        return repository.save(McqQuestion.builder()
                .topic(req.topic())
                .subtopic(req.subtopic())
                .difficulty(req.difficulty())
                .text(req.text().trim())
                .optionsJson(json(req.options()))
                .correctIndex(req.correctIndex())
                .explanation(req.explanation())
                .source(McqQuestion.Source.MANUAL)
                .approved(me.isStaff() ? (req.approved() == null || req.approved()) : false)
                .createdBy(me.userId())
                .build());
    }

    @Transactional
    public McqQuestion update(Long id, McqRequest req) {
        validateOptions(req);
        McqQuestion m = editable(id);
        m.setTopic(req.topic());
        m.setSubtopic(req.subtopic());
        m.setDifficulty(req.difficulty());
        m.setText(req.text().trim());
        m.setOptionsJson(json(req.options()));
        m.setCorrectIndex(req.correctIndex());
        m.setExplanation(req.explanation());
        if (req.approved() != null && CurrentUser.get().isStaff()) {
            m.setApproved(req.approved());
        }
        return m;
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(editable(id));
    }

    @Audited(action = "MCQ_APPROVED", target = "#id")
    @Transactional
    public McqQuestion approve(Long id) {
        McqQuestion m = get(id);
        m.setApproved(true);
        return m;
    }

    private McqQuestion editable(Long id) {
        McqQuestion m = get(id);
        UserPrincipal me = CurrentUser.get();
        if (!me.isStaff() && !me.userId().equals(m.getCreatedBy())) {
            throw ApiException.forbidden("You cannot change this question");
        }
        return m;
    }

    private static void validateOptions(McqRequest req) {
        if (req.correctIndex() >= req.options().size()) {
            throw ApiException.badRequest("Correct option index is out of range");
        }
    }

    /** Generates questions with AI; they are saved unapproved for TPO review. */
    @Transactional
    public List<McqView> generate(GenerateRequest req) {
        List<AssessmentClients.GeneratedMcq> generated;
        try {
            generated = clients.generateMcqs(req.topic().name(), req.subtopic(), req.difficulty().name(), req.count());
        } catch (Exception ex) {
            throw ApiException.unavailable("AI question generation is not available right now");
        }
        List<McqView> out = new ArrayList<>();
        String me = CurrentUser.id();
        for (AssessmentClients.GeneratedMcq g : generated == null ? List.<AssessmentClients.GeneratedMcq>of() : generated) {
            if (g.question() == null || g.options() == null || g.options().size() < 2 || g.correctIndex() == null
                    || g.correctIndex() < 0 || g.correctIndex() >= g.options().size()) {
                continue; // skip malformed AI output
            }
            McqQuestion saved = repository.save(McqQuestion.builder()
                    .topic(req.topic()).subtopic(req.subtopic()).difficulty(req.difficulty())
                    .text(g.question()).optionsJson(json(g.options())).correctIndex(g.correctIndex())
                    .explanation(g.explanation()).source(McqQuestion.Source.AI).approved(false).createdBy(me)
                    .build());
            out.add(view(saved, true));
        }
        return out;
    }

    // ---------------------------------------------------------------- practice

    public List<PracticeQuestion> practiceSet(McqQuestion.Topic topic, McqQuestion.Difficulty difficulty, int count) {
        return repository.randomApproved(topic.name(), difficulty == null ? null : difficulty.name(), Math.min(Math.max(count, 1), 30))
                .stream()
                .map(m -> new PracticeQuestion(m.getId(), m.getTopic(), m.getSubtopic(), m.getDifficulty(), m.getText(), options(m)))
                .toList();
    }

    @Transactional
    public PracticeFeedback answer(PracticeAnswer req) {
        McqQuestion m = get(req.questionId());
        if (!m.isApproved()) {
            throw ApiException.notFound("Question not found");
        }
        boolean correct = m.getCorrectIndex() == req.selectedIndex();
        String userId = CurrentUser.id();
        PracticeStat s = stats.findByUserIdAndTopic(userId, m.getTopic())
                .orElseGet(() -> PracticeStat.builder().userId(userId).topic(m.getTopic()).build());
        s.setAttempted(s.getAttempted() + 1);
        if (correct) {
            s.setCorrect(s.getCorrect() + 1);
        }
        s.setUpdatedAt(LocalDateTime.now());
        stats.save(s);
        return new PracticeFeedback(correct, m.getCorrectIndex(), m.getExplanation());
    }

    public List<TopicStat> practiceStats(String userId) {
        return stats.findByUserId(userId).stream()
                .map(s -> new TopicStat(s.getTopic(), s.getAttempted(), s.getCorrect(),
                        s.getAttempted() == 0 ? 0 : Math.round(1000.0 * s.getCorrect() / s.getAttempted()) / 10.0))
                .toList();
    }

    // ---------------------------------------------------------------- mapping

    public List<String> options(McqQuestion m) {
        try {
            return mapper.readValue(m.getOptionsJson(), new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("Bad options JSON for MCQ {}", m.getId());
            return List.of();
        }
    }

    public McqView view(McqQuestion m, boolean withAnswer) {
        return new McqView(m.getId(), m.getTopic(), m.getSubtopic(), m.getDifficulty(), m.getText(), options(m),
                withAnswer ? m.getCorrectIndex() : null, withAnswer ? m.getExplanation() : null, m.getSource(),
                m.isApproved(), m.getCreatedAt());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("Invalid options");
        }
    }
}
