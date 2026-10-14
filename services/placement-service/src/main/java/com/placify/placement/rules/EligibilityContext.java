package com.placify.placement.rules;

import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Job;
import com.placify.placement.domain.PlacementPolicy;

/**
 * Everything a rule needs to decide whether a student may apply to a job.
 *
 * @param placedTier       highest tier of an accepted/verified offer, or null if not placed
 * @param pptRequired      the job has a pre-placement talk and the policy makes it mandatory
 * @param pptAttended      the student attended that talk
 */
public record EligibilityContext(
        Job job,
        StudentInfo student,
        PlacementPolicy policy,
        boolean alreadyApplied,
        long activeApplications,
        Job.Tier placedTier,
        boolean pptRequired,
        boolean pptAttended) {
}
