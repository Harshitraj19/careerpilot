package com.placify.interview.service;

import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import com.placify.interview.domain.ExperienceVote;
import com.placify.interview.domain.InterviewExperience;
import com.placify.interview.repository.ExperienceVoteRepository;
import com.placify.interview.repository.InterviewExperienceRepository;
import com.placify.interview.web.InterviewDtos.CompanyCount;
import com.placify.interview.web.InterviewDtos.ExperienceRequest;
import com.placify.interview.web.InterviewDtos.ExperienceView;
import com.placify.interview.web.InterviewDtos.ReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Interview experiences: students write, the TPO moderates, everyone reads and upvotes. */
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final InterviewExperienceRepository experiences;
    private final ExperienceVoteRepository votes;
    private final EventPublisher publisher;

    @Transactional
    public ExperienceView submit(ExperienceRequest req) {
        UserPrincipal me = CurrentUser.get();
        InterviewExperience e = experiences.save(InterviewExperience.builder()
                .authorUserId(me.userId())
                .authorName(me.name())
                .anonymous(req.anonymous())
                .companyName(req.companyName().trim())
                .companyKey(InterviewExperience.keyOf(req.companyName()))
                .jobRole(req.jobRole().trim())
                .year(req.year())
                .result(req.result())
                .difficulty(req.difficulty())
                .roundsSummary(req.roundsSummary())
                .content(req.content())
                .questionsAsked(req.questionsAsked())
                .tips(req.tips())
                .status(InterviewExperience.Status.PENDING)
                .upvotes(0)
                .build());
        publisher.notifyUsers(List.of("ROLE:TPO"), "New interview experience to review",
                me.name() + " shared an experience for " + e.getCompanyName(), "EXPERIENCE", "/tpo/experiences", false);
        return view(e, Set.of());
    }

    public Page<ExperienceView> search(String company, String q, int page, int size) {
        String key = company == null || company.isBlank() ? null : InterviewExperience.keyOf(company);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Page<InterviewExperience> result = experiences.searchApproved(key, like,
                PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)),
                        Sort.by(Sort.Order.desc("upvotes"), Sort.Order.desc("createdAt"))));
        Set<Long> voted = votedByMe(result.getContent());
        return result.map(e -> view(e, voted));
    }

    public ExperienceView one(Long id) {
        InterviewExperience e = get(id);
        UserPrincipal me = CurrentUser.get();
        if (e.getStatus() != InterviewExperience.Status.APPROVED && !me.isStaff() && !e.getAuthorUserId().equals(me.userId())) {
            throw ApiException.notFound("Experience not found");
        }
        return view(e, votedByMe(List.of(e)));
    }

    public List<ExperienceView> mine() {
        return experiences.findByAuthorUserIdOrderByCreatedAtDesc(CurrentUser.id()).stream()
                .map(e -> view(e, Set.of())).toList();
    }

    public List<ExperienceView> pending() {
        return experiences.findByStatusOrderByCreatedAtAsc(InterviewExperience.Status.PENDING).stream()
                .map(e -> view(e, Set.of())).toList();
    }

    public List<CompanyCount> companies() {
        return experiences.companiesWithExperiences().stream()
                .map(r -> new CompanyCount((String) r[0], ((Number) r[1]).longValue())).toList();
    }

    @Transactional
    @Audited(action = "EXPERIENCE_REVIEWED", target = "#id")
    public ExperienceView review(Long id, ReviewRequest req) {
        InterviewExperience e = get(id);
        if (e.getStatus() != InterviewExperience.Status.PENDING) {
            throw ApiException.conflict("Already reviewed");
        }
        e.setStatus(req.approve() ? InterviewExperience.Status.APPROVED : InterviewExperience.Status.REJECTED);
        e.setReviewNote(req.note());
        e.setReviewedBy(CurrentUser.id());
        e.setReviewedAt(LocalDateTime.now());
        publisher.notifyUser(e.getAuthorUserId(),
                req.approve() ? "Your interview experience is live" : "Your interview experience was not approved",
                req.approve() ? "Thanks for helping your juniors prepare for " + e.getCompanyName() + "!"
                        : "Reason: " + (req.note() == null ? "not specified" : req.note()),
                "EXPERIENCE", "/student/experiences", false);
        return view(e, Set.of());
    }

    @Transactional
    public ExperienceView toggleVote(Long id) {
        InterviewExperience e = get(id);
        if (e.getStatus() != InterviewExperience.Status.APPROVED) {
            throw ApiException.badRequest("Only published experiences can be upvoted");
        }
        String me = CurrentUser.id();
        if (e.getAuthorUserId().equals(me)) {
            throw ApiException.badRequest("You cannot upvote your own experience");
        }
        var existing = votes.findByExperienceIdAndUserId(id, me);
        if (existing.isPresent()) {
            votes.delete(existing.get());
            experiences.addVotes(id, -1);
            e.setUpvotes(Math.max(0, e.getUpvotes() - 1));
            return view(e, Set.of());
        }
        votes.save(ExperienceVote.builder().experienceId(id).userId(me).build());
        experiences.addVotes(id, 1);
        e.setUpvotes(e.getUpvotes() + 1);
        return view(e, Set.of(id));
    }

    @Transactional
    public void delete(Long id) {
        InterviewExperience e = get(id);
        UserPrincipal me = CurrentUser.get();
        boolean own = e.getAuthorUserId().equals(me.userId()) && e.getStatus() != InterviewExperience.Status.APPROVED;
        if (!own && !me.isStaff()) {
            throw ApiException.forbidden("You can only delete your own unpublished experiences");
        }
        experiences.delete(e);
    }

    private InterviewExperience get(Long id) {
        return experiences.findById(id).orElseThrow(() -> ApiException.notFound("Experience not found"));
    }

    private Set<Long> votedByMe(List<InterviewExperience> list) {
        if (list.isEmpty()) {
            return Set.of();
        }
        return votes.findByUserIdAndExperienceIdIn(CurrentUser.id(), list.stream().map(InterviewExperience::getId).toList())
                .stream().map(ExperienceVote::getExperienceId).collect(Collectors.toSet());
    }

    ExperienceView view(InterviewExperience e, Set<Long> voted) {
        String me = CurrentUser.find().map(UserPrincipal::userId).orElse(null);
        boolean mine = e.getAuthorUserId().equals(me);
        boolean staff = CurrentUser.find().map(UserPrincipal::isStaff).orElse(false);
        String author = e.isAnonymous() && !staff && !mine ? "Anonymous student" : e.getAuthorName();
        return new ExperienceView(e.getId(), author, mine, e.getCompanyName(), e.getJobRole(), e.getYear(), e.getResult(),
                e.getDifficulty(), e.getRoundsSummary(), e.getContent(), e.getQuestionsAsked(), e.getTips(), e.getStatus(),
                mine || staff ? e.getReviewNote() : null, e.getUpvotes(), voted.contains(e.getId()), e.getCreatedAt());
    }
}
