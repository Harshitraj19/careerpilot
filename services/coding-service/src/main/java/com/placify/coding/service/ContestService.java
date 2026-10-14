package com.placify.coding.service;

import com.placify.coding.domain.Contest;
import com.placify.coding.domain.ContestParticipant;
import com.placify.coding.domain.ContestProblemStatus;
import com.placify.coding.domain.ContestQuestion;
import com.placify.coding.domain.Question;
import com.placify.coding.domain.UserRating;
import com.placify.coding.repository.ContestParticipantRepository;
import com.placify.coding.repository.ContestProblemStatusRepository;
import com.placify.coding.repository.ContestQuestionRepository;
import com.placify.coding.repository.ContestRepository;
import com.placify.coding.repository.QuestionRepository;
import com.placify.coding.repository.UserRatingRepository;
import com.placify.coding.web.CodingDtos.ContestProblem;
import com.placify.coding.web.CodingDtos.ContestQuestionRequest;
import com.placify.coding.web.CodingDtos.ContestRequest;
import com.placify.coding.web.CodingDtos.ContestView;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Weekly contests: ICPC-style scoring (points, then penalty minutes), a live
 * leaderboard kept in a Redis sorted set and pushed over WebSocket, and badges
 * for the podium when the contest ends.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contests;
    private final ContestQuestionRepository contestQuestions;
    private final ContestParticipantRepository participants;
    private final ContestProblemStatusRepository problemStatus;
    private final QuestionRepository questions;
    private final UserRatingRepository ratings;
    private final BadgeService badges;
    private final StringRedisTemplate redis;
    private final EventPublisher publisher;

    @Value("${placify.coding.wrong-attempt-penalty-minutes:10}")
    private int wrongPenalty;

    public Contest get(Long id) {
        return contests.findById(id).orElseThrow(() -> ApiException.notFound("Contest not found"));
    }

    @Transactional
    public Contest create(ContestRequest req) {
        validate(req);
        Contest c = contests.save(Contest.builder()
                .title(req.title().trim())
                .description(req.description())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .createdBy(CurrentUser.id())
                .finalized(false)
                .build());
        saveQuestions(c.getId(), req.questions());
        publisher.notifyUsers(List.of("ROLE:STUDENT"), "New contest: " + c.getTitle(),
                "Starts " + c.getStartTime().toLocalDate() + " at " + c.getStartTime().toLocalTime() + ". Join and compete!",
                "CONTEST", "/student/contests/" + c.getId(), false);
        return c;
    }

    @Transactional
    public Contest update(Long id, ContestRequest req) {
        validate(req);
        Contest c = get(id);
        if (c.phase() != Contest.Phase.UPCOMING) {
            throw ApiException.badRequest("Only upcoming contests can be edited");
        }
        c.setTitle(req.title().trim());
        c.setDescription(req.description());
        c.setStartTime(req.startTime());
        c.setEndTime(req.endTime());
        contestQuestions.deleteByContestId(id);
        contestQuestions.flush();
        saveQuestions(id, req.questions());
        return c;
    }

    @Transactional
    public void delete(Long id) {
        Contest c = get(id);
        if (c.phase() == Contest.Phase.RUNNING) {
            throw ApiException.badRequest("A running contest cannot be deleted");
        }
        contests.delete(c);
        redis.delete(boardKey(id));
    }

    private void validate(ContestRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("End time must be after start time");
        }
        if (Duration.between(req.startTime(), req.endTime()).toHours() > 24) {
            throw ApiException.badRequest("A contest can last at most 24 hours");
        }
    }

    private void saveQuestions(Long contestId, List<ContestQuestionRequest> list) {
        int i = 0;
        for (ContestQuestionRequest cq : list) {
            Question q = questions.findById(cq.questionId()).orElseThrow(() -> ApiException.badRequest("Unknown question " + cq.questionId()));
            contestQuestions.save(ContestQuestion.builder().contestId(contestId).questionId(q.getId())
                    .points(cq.points()).orderIndex(i++).build());
        }
    }

    // ---------------------------------------------------------------- participation

    @Transactional
    public void join(Long id) {
        Contest c = get(id);
        if (c.phase() == Contest.Phase.ENDED) {
            throw ApiException.badRequest("This contest has ended");
        }
        UserPrincipal me = CurrentUser.get();
        if (participants.findByContestIdAndUserId(id, me.userId()).isEmpty()) {
            participants.save(ContestParticipant.builder().contestId(id).userId(me.userId()).name(me.name())
                    .score(0).penaltyMinutes(0).build());
            UserRating r = ratings.findById(me.userId()).orElse(null);
            if (r != null) {
                r.setContestsJoined(r.getContestsJoined() + 1);
            }
            safeZadd(id, me.userId(), 0, 0);
        }
    }

    /** True if the user may submit to this question now (contest running and joined). */
    public boolean canSubmit(Long contestId, String userId, String questionId) {
        Contest c = contests.findById(contestId).orElse(null);
        return c != null && c.phase() == Contest.Phase.RUNNING
                && participants.findByContestIdAndUserId(contestId, userId).isPresent()
                && contestQuestions.findByContestIdAndQuestionId(contestId, questionId).isPresent();
    }

    /** Applies a judged contest submission and broadcasts the new leaderboard. */
    @Transactional
    public void onContestSubmission(Long contestId, String userId, String questionId, boolean accepted) {
        Contest c = contests.findById(contestId).orElse(null);
        if (c == null) {
            return;
        }
        ContestParticipant p = participants.findByContestIdAndUserId(contestId, userId).orElse(null);
        ContestQuestion cq = contestQuestions.findByContestIdAndQuestionId(contestId, questionId).orElse(null);
        if (p == null || cq == null) {
            return;
        }
        ContestProblemStatus st = problemStatus.findByContestIdAndUserIdAndQuestionId(contestId, userId, questionId)
                .orElseGet(() -> ContestProblemStatus.builder().contestId(contestId).userId(userId).questionId(questionId).build());
        if (st.isSolved()) {
            return;
        }
        if (accepted) {
            st.setSolved(true);
            st.setSolvedAt(LocalDateTime.now());
            long minutes = Math.max(0, Duration.between(c.getStartTime(), st.getSolvedAt()).toMinutes());
            p.setScore(p.getScore() + cq.getPoints());
            p.setPenaltyMinutes(p.getPenaltyMinutes() + minutes + (long) st.getWrongAttempts() * wrongPenalty);
        } else {
            st.setWrongAttempts(st.getWrongAttempts() + 1);
        }
        problemStatus.save(st);
        safeZadd(contestId, userId, p.getScore(), p.getPenaltyMinutes());
        if (accepted) {
            publisher.publish(RoutingKeys.LEADERBOARD_UPDATED, new Events.LeaderboardUpdatedEvent(contestId, leaderboard(contestId, 50)));
        }
    }

    // ---------------------------------------------------------------- leaderboard

    private static String boardKey(Long contestId) {
        return "placify:contest:" + contestId + ":board";
    }

    /** Composite score: points first, fewer penalty minutes breaks ties. */
    static double composite(int score, long penalty) {
        return score * 1_000_000d - penalty;
    }

    private void safeZadd(Long contestId, String userId, int score, long penalty) {
        try {
            redis.opsForZSet().add(boardKey(contestId), userId, composite(score, penalty));
        } catch (Exception ex) {
            log.warn("Redis leaderboard update failed: {}", ex.getMessage());
        }
    }

    public List<Events.LeaderboardEntry> leaderboard(Long contestId, int limit) {
        Map<String, ContestParticipant> byUser = participants.findByContestIdOrderByScoreDescPenaltyMinutesAsc(contestId)
                .stream().collect(Collectors.toMap(ContestParticipant::getUserId, Function.identity(), (a, b) -> a));
        List<Events.LeaderboardEntry> out = new ArrayList<>();
        try {
            Set<ZSetOperations.TypedTuple<String>> top = redis.opsForZSet().reverseRangeWithScores(boardKey(contestId), 0, limit - 1);
            if (top != null && !top.isEmpty()) {
                int rank = 1;
                for (ZSetOperations.TypedTuple<String> t : top) {
                    ContestParticipant p = byUser.get(t.getValue());
                    if (p != null) {
                        out.add(new Events.LeaderboardEntry(rank++, p.getUserId(), p.getName(), p.getScore(), p.getPenaltyMinutes()));
                    }
                }
                return out;
            }
        } catch (Exception ex) {
            log.warn("Redis leaderboard read failed, using database: {}", ex.getMessage());
        }
        int rank = 1;
        for (ContestParticipant p : participants.findByContestIdOrderByScoreDescPenaltyMinutesAsc(contestId)) {
            if (rank > limit) {
                break;
            }
            out.add(new Events.LeaderboardEntry(rank++, p.getUserId(), p.getName(), p.getScore(), p.getPenaltyMinutes()));
        }
        return out;
    }

    // ---------------------------------------------------------------- views

    public List<ContestView> list() {
        UserPrincipal me = CurrentUser.get();
        return contests.findAllByOrderByStartTimeDesc().stream().map(c -> view(c, me, false)).toList();
    }

    public ContestView view(Contest c, UserPrincipal me, boolean withProblems) {
        boolean joined = participants.findByContestIdAndUserId(c.getId(), me.userId()).isPresent();
        List<ContestProblem> problems = List.of();
        boolean staff = me.role() != Role.STUDENT;
        if (withProblems && (staff || c.phase() != Contest.Phase.UPCOMING)) {
            List<ContestQuestion> cqs = contestQuestions.findByContestIdOrderByOrderIndexAsc(c.getId());
            Map<String, Question> qmap = questions.findByIdIn(cqs.stream().map(ContestQuestion::getQuestionId).toList())
                    .stream().collect(Collectors.toMap(Question::getId, Function.identity()));
            Map<String, ContestProblemStatus> mine = problemStatus.findByContestIdAndUserId(c.getId(), me.userId())
                    .stream().collect(Collectors.toMap(ContestProblemStatus::getQuestionId, Function.identity()));
            problems = cqs.stream().filter(cq -> qmap.containsKey(cq.getQuestionId())).map(cq -> {
                Question q = qmap.get(cq.getQuestionId());
                ContestProblemStatus s = mine.get(q.getId());
                return new ContestProblem(q.getId(), q.getTitle(), q.getDifficulty(), cq.getPoints(),
                        s == null ? null : s.isSolved(), s == null ? null : s.getWrongAttempts());
            }).toList();
        }
        return new ContestView(c.getId(), c.getTitle(), c.getDescription(), c.getStartTime(), c.getEndTime(), c.phase(),
                participants.countByContestId(c.getId()), joined, problems);
    }

    /** Students can open a contest problem only while they take part in the running contest (or after it ends). */
    public Question contestQuestion(Long contestId, String questionId) {
        Contest c = get(contestId);
        UserPrincipal me = CurrentUser.get();
        if (me.role() == Role.STUDENT) {
            if (c.phase() == Contest.Phase.UPCOMING) {
                throw ApiException.forbidden("The contest has not started yet");
            }
            if (c.phase() == Contest.Phase.RUNNING && participants.findByContestIdAndUserId(contestId, me.userId()).isEmpty()) {
                throw ApiException.forbidden("Join the contest first");
            }
        }
        contestQuestions.findByContestIdAndQuestionId(contestId, questionId)
                .orElseThrow(() -> ApiException.notFound("Problem is not part of this contest"));
        return questions.findById(questionId).orElseThrow(() -> ApiException.notFound("Question not found"));
    }

    // ---------------------------------------------------------------- finalisation

    /** Every 5 minutes: freeze finished contests, store ranks and award podium badges. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void finalizeEnded() {
        for (Contest c : contests.findByFinalizedFalseAndEndTimeBefore(LocalDateTime.now())) {
            List<ContestParticipant> ranked = participants.findByContestIdOrderByScoreDescPenaltyMinutesAsc(c.getId());
            for (int i = 0; i < ranked.size(); i++) {
                ContestParticipant p = ranked.get(i);
                p.setFinalRank(i + 1);
                if (p.getScore() <= 0) {
                    continue;
                }
                if (i == 0) {
                    badges.award(p.getUserId(), "CONTEST_WIN_" + c.getId(), "Contest Champion", "Won " + c.getTitle());
                } else if (i < 3) {
                    badges.award(p.getUserId(), "CONTEST_PODIUM_" + c.getId(), "Podium Finish",
                            "Finished #" + (i + 1) + " in " + c.getTitle());
                }
            }
            c.setFinalized(true);
            log.info("Finalized contest {} with {} participants", c.getId(), ranked.size());
        }
    }
}
