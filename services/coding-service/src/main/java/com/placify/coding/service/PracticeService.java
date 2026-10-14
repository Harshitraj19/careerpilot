package com.placify.coding.service;

import com.placify.coding.domain.Question;
import com.placify.coding.domain.UserQuestionStatus;
import com.placify.coding.domain.UserRating;
import com.placify.coding.repository.QuestionRepository;
import com.placify.coding.repository.UserQuestionStatusRepository;
import com.placify.coding.repository.UserRatingRepository;
import com.placify.coding.web.CodingDtos.NextProblem;
import com.placify.coding.web.CodingDtos.PracticeStats;
import com.placify.coding.web.CodingDtos.QuestionSummary;
import com.placify.common.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptive practice: tracks each student's Elo rating and suggests the next
 * problem at the right level, preferring topics where the student struggles.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private final UserRatingRepository ratings;
    private final UserQuestionStatusRepository statuses;
    private final QuestionRepository questions;
    private final BadgeService badges;

    @Value("${placify.coding.starting-rating:1200}")
    private int startingRating;

    public UserRating rating(String userId) {
        return ratings.findById(userId).orElseGet(() -> UserRating.builder().userId(userId).rating(startingRating).build());
    }

    /** Remembers the display name for leaderboards / hall of fame. */
    @Transactional
    public void rememberName(String userId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        UserRating r = ratings.findById(userId).orElseGet(() -> UserRating.builder().userId(userId).rating(startingRating).build());
        if (!name.equals(r.getName())) {
            r.setName(name);
            ratings.save(r);
        }
    }

    /** Applies a judged practice submission. */
    @Transactional
    public void onPracticeSubmission(String userId, String questionId, boolean accepted) {
        Question q = questions.findById(questionId).orElse(null);
        if (q == null) {
            return;
        }
        UserRating r = ratings.findById(userId).orElseGet(() -> ratings.save(
                UserRating.builder().userId(userId).rating(startingRating).build()));
        UserQuestionStatus s = statuses.findByUserIdAndQuestionId(userId, questionId)
                .orElseGet(() -> UserQuestionStatus.builder().userId(userId).questionId(questionId).build());

        r.setSubmissions(r.getSubmissions() + 1);
        q.setAttemptCount(q.getAttemptCount() + 1);
        s.setAttempts(s.getAttempts() + 1);
        s.setLastAttemptAt(LocalDateTime.now());

        if (s.isSolved()) {
            statuses.save(s);
            return; // re-solving does not change ratings
        }
        if (accepted) {
            s.setSolved(true);
            s.setFirstSolvedAt(LocalDateTime.now());
            int before = r.getRating();
            r.setRating(EloRating.update(before, q.getRating(), 1.0, 32));
            q.setRating(EloRating.update(q.getRating(), before, 0.0, 16));
            r.setSolved(r.getSolved() + 1);
            q.setSolvedCount(q.getSolvedCount() + 1);
            switch (q.getDifficulty()) {
                case EASY -> r.setEasySolved(r.getEasySolved() + 1);
                case MEDIUM -> r.setMediumSolved(r.getMediumSolved() + 1);
                case HARD -> r.setHardSolved(r.getHardSolved() + 1);
            }
            badges.onSolved(userId, r);
        } else if (s.getAttempts() <= 3) {
            // only the first few failures count, so one hard problem does not crash the rating
            int before = r.getRating();
            r.setRating(EloRating.update(before, q.getRating(), 0.0, 8));
            q.setRating(EloRating.update(q.getRating(), before, 1.0, 4));
        }
        statuses.save(s);
    }

    /**
     * Picks the next problem: unsolved practice problems rated close to
     * (student rating + 50), preferring the student's weak topics.
     */
    public NextProblem next(String userId) {
        UserRating r = rating(userId);
        List<UserQuestionStatus> mine = statuses.findByUserId(userId);
        Set<String> solved = mine.stream().filter(UserQuestionStatus::isSolved).map(UserQuestionStatus::getQuestionId)
                .collect(Collectors.toSet());
        List<String> weak = weakTags(mine);
        int target = r.getRating() + 50;

        List<Question> pool = questions.findByVisibilityAndActiveTrue(Question.Visibility.PRACTICE).stream()
                .filter(q -> !solved.contains(q.getId()))
                .sorted(Comparator.comparingInt(q -> Math.abs(q.getRating() - target)))
                .limit(8)
                .toList();
        if (pool.isEmpty()) {
            return new NextProblem(null, "You have solved every practice problem. Try a contest!", r.getRating());
        }
        for (Question q : pool) {
            List<String> tags = Texts.toList(q.getTags());
            for (String w : weak) {
                if (tags.contains(w)) {
                    return new NextProblem(QuestionService.summary(q, false),
                            "Matches your level and practises '" + w + "', a topic you found difficult.", r.getRating());
                }
            }
        }
        Question pick = pool.get(0);
        String why = pick.getRating() > r.getRating()
                ? "Slightly above your current level - a good stretch."
                : "Right at your level to build momentum.";
        return new NextProblem(QuestionService.summary(pick, false), why, r.getRating());
    }

    public PracticeStats stats(String userId) {
        UserRating r = rating(userId);
        List<UserQuestionStatus> mine = statuses.findByUserId(userId);
        Map<String, Question> qmap = questions.findByIdIn(mine.stream().map(UserQuestionStatus::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<String, Integer> byTag = new LinkedHashMap<>();
        mine.stream().filter(UserQuestionStatus::isSolved).forEach(s -> {
            Question q = qmap.get(s.getQuestionId());
            if (q != null) {
                Texts.toList(q.getTags()).forEach(t -> byTag.merge(t, 1, Integer::sum));
            }
        });
        List<QuestionSummary> recent = statuses.findByUserIdAndSolvedTrueOrderByFirstSolvedAtDesc(userId).stream()
                .limit(10)
                .map(s -> qmap.get(s.getQuestionId()))
                .filter(java.util.Objects::nonNull)
                .map(q -> QuestionService.summary(q, true))
                .toList();
        Integer rank = null;
        List<UserRating> top = ratings.findByOrderByRatingDesc(PageRequest.of(0, 500));
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).getUserId().equals(userId)) {
                rank = i + 1;
                break;
            }
        }
        return new PracticeStats(r.getRating(), r.getSolved(), r.getSubmissions(), r.getEasySolved(),
                r.getMediumSolved(), r.getHardSolved(), r.getContestsJoined(), byTag, weakTags(mine), recent, rank);
    }

    /** Topics of problems the student attempted but has not solved yet. */
    private List<String> weakTags(List<UserQuestionStatus> mine) {
        Map<String, Question> qmap = questions.findByIdIn(mine.stream().filter(s -> !s.isSolved())
                        .map(UserQuestionStatus::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<String, Integer> counts = new HashMap<>();
        for (UserQuestionStatus s : mine) {
            if (!s.isSolved() && qmap.containsKey(s.getQuestionId())) {
                Texts.toList(qmap.get(s.getQuestionId()).getTags()).forEach(t -> counts.merge(t, s.getAttempts(), Integer::sum));
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).toList();
    }
}
