package com.placify.analytics.service;

import com.placify.analytics.AnalyticsProperties;
import com.placify.analytics.client.AnalyticsClients;
import com.placify.analytics.client.AnalyticsClients.AssessmentStats;
import com.placify.analytics.client.AnalyticsClients.CodingStats;
import com.placify.analytics.client.AnalyticsClients.MockStats;
import com.placify.analytics.client.AnalyticsClients.StudentSummary;
import com.placify.analytics.domain.ReadinessSnapshot;
import com.placify.analytics.repository.ReadinessSnapshotRepository;
import com.placify.analytics.service.ReadinessCalculator.Components;
import com.placify.analytics.web.AnalyticsDtos.Readiness;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/** Computes, caches and explains readiness scores. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadinessService {

    private final AnalyticsClients clients;
    private final ReadinessSnapshotRepository snapshots;
    private final AnalyticsProperties properties;

    /** Cached score if fresh enough, otherwise recomputed. */
    public ReadinessSnapshot get(String userId) {
        Optional<ReadinessSnapshot> s = snapshots.findById(userId);
        if (s.isPresent() && s.get().getComputedAt().isAfter(LocalDateTime.now().minusMinutes(properties.getReadinessCacheMinutes()))) {
            return s.get();
        }
        return compute(userId);
    }

    public ReadinessSnapshot compute(String userId) {
        StudentSummary student = clients.student(userId).orElse(null);
        return compute(userId, student);
    }

    public ReadinessSnapshot compute(String userId, StudentSummary student) {
        CodingStats coding = clients.coding(userId).orElse(null);
        AssessmentStats aptitude = clients.assessment(userId).orElse(null);
        MockStats mock = clients.mock(userId).orElse(null);
        Components c = ReadinessCalculator.compute(coding, aptitude, student == null ? null : student.bestAtsScore(),
                student == null ? 0 : student.completeness(), mock);
        ReadinessSnapshot snap = snapshots.findById(userId).orElseGet(() -> ReadinessSnapshot.builder().userId(userId).build());
        snap.setScore(c.total());
        snap.setCoding(c.coding());
        snap.setAptitude(c.aptitude());
        snap.setAts(c.ats());
        snap.setProfile(c.profile());
        snap.setMock(c.mock());
        snap.setContests(c.contests());
        if (student != null) {
            snap.setCgpa(student.cgpa());
            snap.setBacklogs(student.backlogs());
            snap.setBranch(student.branch());
            snap.setBatch(student.batch());
        }
        snap.setComputedAt(LocalDateTime.now());
        return snapshots.save(snap);
    }

    public Readiness explain(ReadinessSnapshot s) {
        Components c = components(s);
        return new Readiness(s.getUserId(), s.getScore(), level(s.getScore()), c, ReadinessCalculator.suggestions(c),
                s.getComputedAt());
    }

    public static Components components(ReadinessSnapshot s) {
        return new Components(s.getCoding(), s.getAptitude(), s.getAts(), s.getProfile(), s.getMock(), s.getContests());
    }

    static String level(int score) {
        if (score >= 80) {
            return "Excellent";
        }
        if (score >= 60) {
            return "Good";
        }
        if (score >= 40) {
            return "Developing";
        }
        return "Needs attention";
    }

    /** Weakest area relative to its maximum. */
    public static String weakestArea(ReadinessSnapshot s) {
        double[] ratios = {
                s.getCoding() / ReadinessCalculator.CODING_MAX,
                s.getAptitude() / ReadinessCalculator.APTITUDE_MAX,
                s.getAts() / ReadinessCalculator.ATS_MAX,
                s.getProfile() / ReadinessCalculator.PROFILE_MAX,
                s.getMock() / ReadinessCalculator.MOCK_MAX,
                s.getContests() / ReadinessCalculator.CONTESTS_MAX};
        String[] names = {"Coding", "Aptitude", "Resume", "Profile", "Mock interviews", "Contests"};
        int min = 0;
        for (int i = 1; i < ratios.length; i++) {
            if (ratios[i] < ratios[min]) {
                min = i;
            }
        }
        return names[min];
    }
}
