package com.placify.placement.service;

import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Application;
import com.placify.placement.domain.CalendarEvent;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.Offer;
import com.placify.placement.domain.PlacementPolicy;
import com.placify.placement.repository.ApplicationRepository;
import com.placify.placement.repository.CalendarEventRepository;
import com.placify.placement.repository.EventAttendanceRepository;
import com.placify.placement.repository.OfferRepository;
import com.placify.placement.rules.EligibilityContext;
import com.placify.placement.rules.EligibilityRule;
import com.placify.placement.rules.RuleCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Runs the rule chain for a student and a job.
 */
@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final List<EligibilityRule> rules;
    private final PolicyService policyService;
    private final ApplicationRepository applications;
    private final OfferRepository offers;
    private final CalendarEventRepository events;
    private final EventAttendanceRepository attendance;

    public record EligibilityReport(boolean eligible, List<RuleCheck> checks) {

        public List<String> reasons() {
            return checks.stream().filter(c -> c.blocking() && !c.passed()).map(RuleCheck::message).toList();
        }
    }

    public EligibilityReport evaluate(Job job, StudentInfo student) {
        PlacementPolicy policy = policyService.get();
        EligibilityContext ctx = buildContext(job, student, policy);
        List<RuleCheck> checks = new ArrayList<>();
        for (EligibilityRule rule : rules) {
            RuleCheck check = rule.evaluate(ctx);
            if (check != null) {
                checks.add(check);
            }
        }
        boolean eligible = checks.stream().noneMatch(c -> c.blocking() && !c.passed());
        return new EligibilityReport(eligible, checks);
    }

    /** Only the academic criteria; used to find which students to notify about a new job. */
    public boolean academicallyEligible(Job job, StudentInfo s) {
        if (job.getEligibleBatch() != null && !job.getEligibleBatch().equals(s.batch())) {
            return false;
        }
        var branches = com.placify.common.util.Texts.toSkillSet(job.getAllowedBranches());
        if (!branches.isEmpty() && (s.branch() == null || !branches.contains(s.branch().trim().toLowerCase()))) {
            return false;
        }
        if (job.getMinCgpa() != null && (s.cgpa() == null || s.cgpa().compareTo(job.getMinCgpa()) < 0)) {
            return false;
        }
        if (job.getMaxBacklogs() != null && s.backlogs() > job.getMaxBacklogs()) {
            return false;
        }
        if (job.getMinTenth() != null && (s.tenthPercentage() == null || s.tenthPercentage().compareTo(job.getMinTenth()) < 0)) {
            return false;
        }
        return job.getMinTwelfth() == null
                || (s.twelfthPercentage() != null && s.twelfthPercentage().compareTo(job.getMinTwelfth()) >= 0);
    }

    private EligibilityContext buildContext(Job job, StudentInfo student, PlacementPolicy policy) {
        // a withdrawn application does not block applying again while the drive is open
        boolean applied = applications.findByJobIdAndStudentUserId(job.getId(), student.userId())
                .map(a -> a.getStatus() != Application.Status.WITHDRAWN).orElse(false);
        long active = applications.countByStudentUserIdAndStatusIn(student.userId(),
                EnumSet.of(Application.Status.APPLIED, Application.Status.SHORTLISTED, Application.Status.IN_PROCESS));

        Job.Tier placedTier = placedTier(student.userId());

        boolean pptRequired = false;
        boolean pptAttended = false;
        if (policy.isPptMandatory()) {
            List<Long> pptIds = events.findByJobIdAndType(job.getId(), CalendarEvent.Type.PRE_PLACEMENT_TALK)
                    .stream().map(CalendarEvent::getId).toList();
            pptRequired = !pptIds.isEmpty();
            pptAttended = pptRequired && attendance.existsByEventIdInAndStudentUserId(pptIds, student.userId());
        }
        return new EligibilityContext(job, student, policy, applied, active, placedTier, pptRequired, pptAttended);
    }

    /** Highest tier among the student's accepted or verified on-campus offers. */
    public Job.Tier placedTier(String studentUserId) {
        return offers.findByStudentUserIdAndStatusIn(studentUserId, EnumSet.of(Offer.Status.ACCEPTED, Offer.Status.VERIFIED))
                .stream()
                .filter(o -> !o.isOffCampus() || o.getStatus() == Offer.Status.VERIFIED)
                .map(Offer::getTier)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);
    }
}
