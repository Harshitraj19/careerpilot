package com.placify.placement.rules;

import com.placify.common.util.Texts;
import com.placify.placement.domain.Job;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * The concrete rules. Each nested class is a separate Spring bean and the
 * {@link Order} defines the order shown to the student.
 */
public final class PlacementRules {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private PlacementRules() {
    }

    /** The job must be published and not closed. */
    @Component
    @Order(10)
    public static class JobOpenRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            return ctx.job().getStatus() == Job.Status.OPEN
                    ? RuleCheck.pass("JOB_OPEN", "Job open", "Applications are open")
                    : RuleCheck.fail("JOB_OPEN", "Job open", "This job is not accepting applications");
        }
    }

    @Component
    @Order(20)
    public static class DeadlineRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            LocalDateTime deadline = ctx.job().getApplicationDeadline();
            if (deadline == null) {
                return null;
            }
            return deadline.isAfter(LocalDateTime.now())
                    ? RuleCheck.pass("DEADLINE", "Deadline", "Apply before " + DATE.format(deadline))
                    : RuleCheck.fail("DEADLINE", "Deadline", "The deadline (" + DATE.format(deadline) + ") has passed");
        }
    }

    @Component
    @Order(30)
    public static class BatchRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            Integer required = ctx.job().getEligibleBatch();
            if (required == null) {
                return null;
            }
            Integer batch = ctx.student().batch();
            return required.equals(batch)
                    ? RuleCheck.pass("BATCH", "Batch", "Open to the " + required + " batch")
                    : RuleCheck.fail("BATCH", "Batch", "Only for the " + required + " batch (yours: " + batch + ")");
        }
    }

    @Component
    @Order(40)
    public static class BranchRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            Set<String> allowed = Texts.toSkillSet(ctx.job().getAllowedBranches());
            if (allowed.isEmpty()) {
                return null;
            }
            String branch = ctx.student().branch() == null ? "" : ctx.student().branch().trim().toLowerCase();
            return allowed.contains(branch)
                    ? RuleCheck.pass("BRANCH", "Branch", "Your branch is eligible")
                    : RuleCheck.fail("BRANCH", "Branch", "Only for: " + ctx.job().getAllowedBranches());
        }
    }

    @Component
    @Order(50)
    public static class CgpaRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            BigDecimal min = ctx.job().getMinCgpa();
            if (min == null) {
                return null;
            }
            BigDecimal cgpa = ctx.student().cgpa();
            if (cgpa == null) {
                return RuleCheck.fail("CGPA", "CGPA", "Your CGPA is not recorded. Contact the placement office.");
            }
            return cgpa.compareTo(min) >= 0
                    ? RuleCheck.pass("CGPA", "CGPA", "Minimum " + min + " (yours: " + cgpa + ")")
                    : RuleCheck.fail("CGPA", "CGPA", "Minimum CGPA is " + min + " (yours: " + cgpa + ")");
        }
    }

    @Component
    @Order(60)
    public static class BacklogRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            Integer max = ctx.job().getMaxBacklogs();
            if (max == null) {
                return null;
            }
            int backlogs = ctx.student().backlogs();
            return backlogs <= max
                    ? RuleCheck.pass("BACKLOGS", "Backlogs", "Up to " + max + " allowed (yours: " + backlogs + ")")
                    : RuleCheck.fail("BACKLOGS", "Backlogs", "At most " + max + " backlog(s) allowed (yours: " + backlogs + ")");
        }
    }

    @Component
    @Order(70)
    public static class SchoolMarksRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            BigDecimal minTenth = ctx.job().getMinTenth();
            BigDecimal minTwelfth = ctx.job().getMinTwelfth();
            if (minTenth == null && minTwelfth == null) {
                return null;
            }
            BigDecimal tenth = ctx.student().tenthPercentage();
            BigDecimal twelfth = ctx.student().twelfthPercentage();
            if ((minTenth != null && tenth == null) || (minTwelfth != null && twelfth == null)) {
                return RuleCheck.fail("SCHOOL_MARKS", "10th / 12th", "Add your 10th and 12th percentages to your profile");
            }
            boolean ok = (minTenth == null || tenth.compareTo(minTenth) >= 0)
                    && (minTwelfth == null || twelfth.compareTo(minTwelfth) >= 0);
            String need = (minTenth == null ? "" : "10th ≥ " + minTenth + "% ")
                    + (minTwelfth == null ? "" : "12th ≥ " + minTwelfth + "%");
            return ok
                    ? RuleCheck.pass("SCHOOL_MARKS", "10th / 12th", "Meets " + need.trim())
                    : RuleCheck.fail("SCHOOL_MARKS", "10th / 12th", "Requires " + need.trim());
        }
    }

    @Component
    @Order(80)
    public static class ResumeRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            if (!ctx.policy().isResumeRequired()) {
                return null;
            }
            return ctx.student().hasResume()
                    ? RuleCheck.pass("RESUME", "Resume", "Resume uploaded")
                    : RuleCheck.fail("RESUME", "Resume", "Upload your resume before applying");
        }
    }

    @Component
    @Order(90)
    public static class AlreadyAppliedRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            return ctx.alreadyApplied()
                    ? RuleCheck.fail("ALREADY_APPLIED", "Application", "You have already applied to this job")
                    : null;
        }
    }

    /** College rule: limit the number of applications in progress at the same time. */
    @Component
    @Order(100)
    public static class MaxApplicationsRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            Integer max = ctx.policy().getMaxActiveApplications();
            if (max == null) {
                return null;
            }
            return ctx.activeApplications() < max
                    ? RuleCheck.pass("MAX_APPLICATIONS", "Application limit",
                    ctx.activeApplications() + " of " + max + " active applications used")
                    : RuleCheck.fail("MAX_APPLICATIONS", "Application limit",
                    "You already have " + max + " applications in progress (college limit)");
        }
    }

    /**
     * College rule: Dream / Super-Dream tiers with one-offer policy. Once placed,
     * a student may only apply to jobs of a strictly higher tier.
     */
    @Component
    @Order(110)
    public static class OneOfferRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            if (!ctx.policy().isOneOfferPolicy()) {
                return null;
            }
            Job.Tier placed = ctx.placedTier();
            Job.Tier jobTier = ctx.job().getTier();
            if (placed == null) {
                return RuleCheck.pass("ONE_OFFER", "Placement policy", "Not placed yet - all tiers open (this job: "
                        + label(jobTier) + ")");
            }
            return jobTier.ordinal() > placed.ordinal()
                    ? RuleCheck.pass("ONE_OFFER", "Placement policy", "You are placed in " + label(placed)
                    + "; this " + label(jobTier) + " job is a higher tier")
                    : RuleCheck.fail("ONE_OFFER", "Placement policy", "You are already placed in " + label(placed)
                    + ". One-offer policy only allows higher-tier companies.");
        }

        private static String label(Job.Tier tier) {
            return switch (tier) {
                case NORMAL -> "Normal";
                case DREAM -> "Dream";
                case SUPER_DREAM -> "Super-Dream";
            };
        }
    }

    /** College rule: attend the company's pre-placement talk before applying. */
    @Component
    @Order(120)
    public static class PptAttendanceRule implements EligibilityRule {
        @Override
        public RuleCheck evaluate(EligibilityContext ctx) {
            if (!ctx.pptRequired()) {
                return null;
            }
            return ctx.pptAttended()
                    ? RuleCheck.pass("PPT", "Pre-placement talk", "Attendance recorded")
                    : RuleCheck.fail("PPT", "Pre-placement talk", "Attend the pre-placement talk to unlock this job");
        }
    }
}
