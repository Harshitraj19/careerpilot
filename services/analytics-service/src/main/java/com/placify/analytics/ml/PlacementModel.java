package com.placify.analytics.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Placement-chance model: logistic regression trained in Java with Oracle
 * Tribuo on past seasons (recorded + synthetic). Retrained nightly and on demand.
 */
@Slf4j
@Component
public class PlacementModel {

    static final String PLACED = "PLACED";
    static final String NOT_PLACED = "NOT_PLACED";

    private final LabelFactory labels = new LabelFactory();

    private volatile Model<Label> model;
    private volatile ModelInfo info = new ModelInfo(false, 0, 0, null, null, null, Map.of(), "Not trained yet");

    public record ModelInfo(boolean trained, int examples, int placedExamples, Double accuracy, Double auc,
                            LocalDateTime trainedAt, Map<String, Double> weights, String note) {
    }

    public record Training(Features features, boolean placed) {
    }

    public synchronized ModelInfo train(List<Training> data) {
        if (data.size() < 50) {
            info = new ModelInfo(false, data.size(), 0, null, null, null, Map.of(), "Need at least 50 examples");
            return info;
        }
        try {
            List<Training> shuffled = new ArrayList<>(data);
            Collections.shuffle(shuffled, new Random(7));
            int cut = (int) (shuffled.size() * 0.8);
            MutableDataset<Label> train = dataset(shuffled.subList(0, cut), "train");
            MutableDataset<Label> test = dataset(shuffled.subList(cut, shuffled.size()), "test");

            Model<Label> candidate = new LogisticRegressionTrainer().train(train);
            LabelEvaluation eval = new LabelEvaluator().evaluate(candidate, test);
            Double auc = null;
            try {
                auc = eval.AUCROC(new Label(PLACED));
            } catch (RuntimeException ex) {
                log.debug("AUC not available: {}", ex.getMessage());
            }
            // final model on all data
            Model<Label> full = new LogisticRegressionTrainer().train(dataset(shuffled, "all"));
            model = full;
            info = new ModelInfo(true, data.size(), (int) data.stream().filter(Training::placed).count(),
                    round(eval.accuracy()), auc == null ? null : round(auc), LocalDateTime.now(), weights(full),
                    "Logistic regression (Tribuo, AdaGrad SGD), 80/20 hold-out evaluation");
            log.info("Placement model trained on {} examples, accuracy {}, AUC {}", data.size(), info.accuracy(), info.auc());
        } catch (Exception ex) {
            log.warn("Model training failed, using the built-in formula: {}", ex.getMessage());
            info = new ModelInfo(false, data.size(), 0, null, null, null, Map.of(), "Training failed: " + ex.getMessage());
        }
        return info;
    }

    /** Probability (0..1) that a student with these features gets placed. */
    public double probability(Features f) {
        Model<Label> m = model;
        if (m != null) {
            try {
                Prediction<Label> p = m.predict(new ArrayExample<>(LabelFactory.UNKNOWN_LABEL, Features.NAMES, f.vector()));
                Label placed = p.getOutputScores().get(PLACED);
                if (placed != null) {
                    return placed.getScore();
                }
            } catch (Exception ex) {
                log.debug("Prediction failed: {}", ex.getMessage());
            }
        }
        double logit = SyntheticHistory.trueLogit(f.cgpa(), f.backlogs(), f.coding(), f.aptitude(), f.ats(), f.mock(), f.contests());
        return 1 / (1 + Math.exp(-logit));
    }

    public ModelInfo info() {
        return info;
    }

    private MutableDataset<Label> dataset(List<Training> rows, String name) {
        MutableDataset<Label> ds = new MutableDataset<>(
                new SimpleDataSourceProvenance("placify-history-" + name, OffsetDateTime.now(), labels), labels);
        for (Training t : rows) {
            ds.add(new ArrayExample<>(new Label(t.placed() ? PLACED : NOT_PLACED), Features.NAMES, t.features().vector()));
        }
        return ds;
    }

    private static Map<String, Double> weights(Model<Label> m) {
        Map<String, Double> out = new LinkedHashMap<>();
        try {
            var top = m.getTopFeatures(Features.NAMES.length + 1);
            var forPlaced = top.get(PLACED);
            if (forPlaced != null) {
                for (var pair : forPlaced) {
                    out.put(pair.getA(), round(pair.getB()));
                }
            }
        } catch (Exception ex) {
            // weights are informative only
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
