package com.placify.placement.service;

import com.placify.common.util.Texts;
import com.placify.placement.client.PlacementClients;
import com.placify.placement.client.StudentInfo;
import com.placify.placement.domain.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Smart job matching and skill-gap analysis.
 *
 * <pre>
 * match % = 50% skill overlap (Jaccard-style, by required skills)
 *         + 30% semantic similarity of the job text and the student's skills/resume (DJL embeddings)
 *         + 20% CGPA fit (how far above the cut-off)
 * </pre>
 * The quick version (job lists) skips the semantic part.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private final PlacementClients clients;

    public record MatchResult(
            int matchPercent,
            int skillPercent,
            Integer semanticPercent,
            int cgpaFitPercent,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<PlacementClients.ProblemSuggestion> practiceProblems,
            String advice) {
    }

    /** Fast score for lists (no remote calls). */
    public int quickScore(Job job, StudentInfo s) {
        int skill = skillPercent(job, s);
        int cgpa = cgpaFit(job, s);
        return (int) Math.round(skill * 0.7 + cgpa * 0.3);
    }

    public MatchResult fullMatch(Job job, StudentInfo s) {
        Set<String> required = Texts.toSkillSet(job.getRequiredSkills());
        Set<String> has = normalized(s.skills());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String r : required) {
            (hasSkill(has, r) ? matched : missing).add(r);
        }
        int skill = skillPercent(job, s);
        int cgpa = cgpaFit(job, s);

        String jobText = job.getTitle() + ". " + Texts.truncate(job.getDescription(), 3000) + " Skills: " + job.getRequiredSkills();
        String studentText = "Skills: " + String.join(", ", s.skills() == null ? List.of() : s.skills()) + ". "
                + Texts.truncate(clients.resumeText(s.userId()), 3000);
        double sim = clients.similarity(jobText, studentText);
        Integer semantic = sim < 0 ? null : (int) Math.round(Math.max(0, Math.min(1, (sim - 0.2) / 0.6)) * 100);

        int match = semantic == null
                ? (int) Math.round(skill * 0.7 + cgpa * 0.3)
                : (int) Math.round(skill * 0.5 + semantic * 0.3 + cgpa * 0.2);

        List<PlacementClients.ProblemSuggestion> practice = clients.recommendProblems(missing.isEmpty() ? required : missing, 5);
        return new MatchResult(match, skill, semantic, cgpa, matched, missing, practice, advice(match, missing));
    }

    private static String advice(int match, List<String> missing) {
        if (missing.isEmpty() && match >= 75) {
            return "Strong match. Revise the fundamentals of the listed skills and apply.";
        }
        if (missing.isEmpty()) {
            return "You have the listed skills. Strengthen them with practice problems before the test.";
        }
        String gap = String.join(", ", missing.subList(0, Math.min(4, missing.size())));
        return match >= 50
                ? "Good match. Close the gap in: " + gap + "."
                : "You lack " + gap + ". Practise the suggested problems and add projects using these skills.";
    }

    int skillPercent(Job job, StudentInfo s) {
        Set<String> required = Texts.toSkillSet(job.getRequiredSkills());
        if (required.isEmpty()) {
            return 70; // neutral when the company listed no skills
        }
        Set<String> has = normalized(s.skills());
        long hit = required.stream().filter(r -> hasSkill(has, r)).count();
        return (int) Math.round(100.0 * hit / required.size());
    }

    int cgpaFit(Job job, StudentInfo s) {
        if (s.cgpa() == null) {
            return 0;
        }
        BigDecimal min = job.getMinCgpa() == null ? new BigDecimal("6.0") : job.getMinCgpa();
        if (s.cgpa().compareTo(min) < 0) {
            return 0;
        }
        // 60% at the cut-off, rising to 100% at 10
        BigDecimal range = BigDecimal.TEN.subtract(min);
        if (range.signum() <= 0) {
            return 100;
        }
        double above = s.cgpa().subtract(min).divide(range, 4, RoundingMode.HALF_UP).doubleValue();
        return (int) Math.round(60 + 40 * above);
    }

    private static Set<String> normalized(List<String> skills) {
        Set<String> set = new LinkedHashSet<>();
        if (skills != null) {
            skills.forEach(x -> set.add(x.trim().toLowerCase()));
        }
        return set;
    }

    /** "spring boot" matches "spring boot", "springboot" and "spring-boot". */
    private static boolean hasSkill(Set<String> has, String required) {
        String r = squash(required);
        return has.stream().anyMatch(h -> squash(h).equals(r) || (r.length() > 3 && squash(h).contains(r)));
    }

    private static String squash(String s) {
        return s.toLowerCase().replaceAll("[\\s.\\-_]", "");
    }
}
