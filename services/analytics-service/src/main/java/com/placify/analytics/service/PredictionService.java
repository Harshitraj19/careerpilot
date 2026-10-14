package com.placify.analytics.service;

import com.placify.analytics.AnalyticsProperties;
import com.placify.analytics.client.AnalyticsClients;
import com.placify.analytics.client.AnalyticsClients.PlacementSummary;
import com.placify.analytics.client.AnalyticsClients.StudentSummary;
import com.placify.analytics.domain.HistoricalOutcome;
import com.placify.analytics.domain.ReadinessSnapshot;
import com.placify.analytics.ml.Features;
import com.placify.analytics.ml.PlacementModel;
import com.placify.analytics.ml.PlacementModel.ModelInfo;
import com.placify.analytics.ml.SyntheticHistory;
import com.placify.analytics.repository.HistoricalOutcomeRepository;
import com.placify.analytics.web.AnalyticsDtos.Factor;
import com.placify.analytics.web.AnalyticsDtos.Prediction;
import com.placify.analytics.web.AnalyticsDtos.SeasonRecordResult;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Placement-chance prediction. The model learns from past seasons: synthetic
 * data on day one, then real seasons the TPO records at the end of each year.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PlacementModel model;
    private final HistoricalOutcomeRepository history;
    private final ReadinessService readiness;
    private final AnalyticsClients clients;
    private final AnalyticsProperties properties;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            seedIfEmpty();
            retrain();
        } catch (Exception ex) {
            log.warn("Initial model training skipped: {}", ex.getMessage());
        }
    }

    @Transactional
    public void seedIfEmpty() {
        if (history.count() > 0) {
            return;
        }
        List<HistoricalOutcome> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SyntheticHistory.Row r : SyntheticHistory.generate(properties.getSyntheticHistorySize(), 42L)) {
            Features f = r.features();
            rows.add(HistoricalOutcome.builder().source(HistoricalOutcome.SYNTHETIC).cgpa(f.cgpa()).backlogs(f.backlogs())
                    .coding(f.coding()).aptitude(f.aptitude()).ats(f.ats()).mock(f.mock()).contests(f.contests())
                    .placed(r.placed()).createdAt(now).build());
        }
        history.saveAll(rows);
        log.info("Seeded {} synthetic historical outcomes", rows.size());
    }

    @Scheduled(cron = "0 15 3 * * *")
    public ModelInfo retrain() {
        List<PlacementModel.Training> data = history.findAll().stream()
                .map(h -> new PlacementModel.Training(new Features(h.getCgpa(), h.getBacklogs(), h.getCoding(),
                        h.getAptitude(), h.getAts(), h.getMock(), h.getContests()), h.isPlaced()))
                .toList();
        return model.train(data);
    }

    public ModelInfo info() {
        return model.info();
    }

    public Prediction predict(String userId) {
        ReadinessSnapshot s = readiness.get(userId);
        Features f = features(s);
        double p = model.probability(f);
        int percent = (int) Math.round(p * 100);
        return new Prediction(userId, Math.round(p * 1000) / 1000.0, percent, band(percent), factors(f),
                model.info().trained(),
                "An estimate from past placement seasons, not a guarantee. Improving your weakest areas raises it.");
    }

    public static Features features(ReadinessSnapshot s) {
        return new Features(
                s.getCgpa() == null ? 0 : s.getCgpa().doubleValue(),
                s.getBacklogs(),
                pct(s.getCoding(), ReadinessCalculator.CODING_MAX),
                pct(s.getAptitude(), ReadinessCalculator.APTITUDE_MAX),
                pct(s.getAts(), ReadinessCalculator.ATS_MAX),
                pct(s.getMock(), ReadinessCalculator.MOCK_MAX),
                pct(s.getContests(), ReadinessCalculator.CONTESTS_MAX));
    }

    public int percentFor(ReadinessSnapshot s) {
        return (int) Math.round(model.probability(features(s)) * 100);
    }

    /**
     * End of season: snapshot every student of the batch with their final
     * outcome as training data, then retrain. Can be run once per batch.
     */
    @Transactional
    public SeasonRecordResult recordSeason(int batch) {
        if (history.countByBatch(batch) > 0) {
            throw ApiException.conflict("Season " + batch + " has already been recorded");
        }
        List<StudentSummary> students = clients.students(null, batch);
        if (students.isEmpty()) {
            throw ApiException.badRequest("No students found for batch " + batch);
        }
        List<HistoricalOutcome> rows = new ArrayList<>();
        int placed = 0;
        for (StudentSummary st : students) {
            ReadinessSnapshot s = readiness.compute(st.userId(), st);
            boolean isPlaced = clients.placement(st.userId()).map(PlacementSummary::placed).orElse(false);
            placed += isPlaced ? 1 : 0;
            Features f = features(s);
            rows.add(HistoricalOutcome.builder().source(HistoricalOutcome.RECORDED).batch(batch).cgpa(f.cgpa())
                    .backlogs(f.backlogs()).coding(f.coding()).aptitude(f.aptitude()).ats(f.ats()).mock(f.mock())
                    .contests(f.contests()).placed(isPlaced).createdAt(LocalDateTime.now()).build());
        }
        history.saveAll(rows);
        ModelInfo info = retrain();
        return new SeasonRecordResult(batch, rows.size(), placed, info);
    }

    /** Plain-language reasons, from the student's own numbers. */
    static List<Factor> factors(Features f) {
        List<Factor> out = new ArrayList<>();
        if (f.backlogs() > 0) {
            out.add(new Factor("Backlogs", "NEGATIVE", f.backlogs() + " active backlog(s) - many companies require none"));
        }
        if (f.cgpa() >= 8) {
            out.add(new Factor("CGPA", "POSITIVE", "CGPA " + f.cgpa() + " clears most cut-offs"));
        } else if (f.cgpa() > 0 && f.cgpa() < 7) {
            out.add(new Factor("CGPA", "NEGATIVE", "CGPA " + f.cgpa() + " is below the common 7.0 cut-off"));
        }
        rate(out, "Coding", f.coding());
        rate(out, "Aptitude", f.aptitude());
        rate(out, "Resume (ATS)", f.ats());
        rate(out, "Mock interviews", f.mock());
        rate(out, "Contests", f.contests());
        return out;
    }

    private static void rate(List<Factor> out, String name, double percent) {
        if (percent >= 70) {
            out.add(new Factor(name, "POSITIVE", "Strong (" + Math.round(percent) + "% of the maximum)"));
        } else if (percent < 35) {
            out.add(new Factor(name, "NEGATIVE", "Weak (" + Math.round(percent) + "% of the maximum) - improve this first"));
        }
    }

    static String band(int percent) {
        if (percent >= 75) {
            return "High";
        }
        if (percent >= 45) {
            return "Moderate";
        }
        return "Low";
    }

    private static double pct(double value, int max) {
        return max == 0 ? 0 : Math.min(100, value * 100.0 / max);
    }
}
