package com.placify.proctoring.vision;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.placify.proctoring.config.ProctoringProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

/**
 * Detects mobile phones in a frame with a YOLOv5 object-detection model run in
 * Java by DJL (Deep Java Library, PyTorch engine). The model is downloaded and
 * loaded in the background at startup; until then (or if it cannot be loaded)
 * phone detection is simply skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneDetector {

    private static final List<String> PHONE_LABELS = List.of("cell phone", "cellphone", "mobile phone", "phone");

    private final ProctoringProperties props;
    private volatile ZooModel<Image, DetectedObjects> model;

    @PostConstruct
    void loadInBackground() {
        if (!props.phoneDetection().enabled()) {
            log.info("Phone detection disabled by configuration");
            return;
        }
        Thread loader = new Thread(() -> {
            try {
                Criteria<Image, DetectedObjects> criteria = Criteria.builder()
                        .setTypes(Image.class, DetectedObjects.class)
                        .optModelUrls(props.phoneDetection().modelUrl())
                        .optEngine("PyTorch")
                        .build();
                model = criteria.loadModel();
                log.info("Phone detection model loaded ({})", props.phoneDetection().modelUrl());
            } catch (Exception | Error ex) {
                log.warn("Phone detection unavailable: {}", ex.getMessage());
            }
        }, "phone-model-loader");
        loader.setDaemon(true);
        loader.start();
    }

    public boolean available() {
        return model != null;
    }

    /** @return the confidence of the best phone detection, if any above the threshold */
    public Optional<Double> detect(byte[] imageBytes) {
        ZooModel<Image, DetectedObjects> m = model;
        if (m == null) {
            return Optional.empty();
        }
        try (Predictor<Image, DetectedObjects> predictor = m.newPredictor()) {
            Image image = ImageFactory.getInstance().fromInputStream(new ByteArrayInputStream(imageBytes));
            DetectedObjects detections = predictor.predict(image);
            double best = 0;
            List<DetectedObjects.DetectedObject> items = detections.items();
            for (DetectedObjects.DetectedObject d : items) {
                if (PHONE_LABELS.contains(d.getClassName().toLowerCase()) && d.getProbability() > best) {
                    best = d.getProbability();
                }
            }
            return best >= props.phoneDetection().minConfidence() ? Optional.of(best) : Optional.empty();
        } catch (Exception ex) {
            log.debug("Phone detection failed on a frame: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() {
        if (model != null) {
            model.close();
        }
    }
}
