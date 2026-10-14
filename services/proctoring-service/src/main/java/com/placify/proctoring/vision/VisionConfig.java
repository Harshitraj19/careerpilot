package com.placify.proctoring.vision;

import com.placify.proctoring.config.ProctoringProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the identity matcher: the DJL face-embedding model when a model URL
 * is configured and loads, otherwise the built-in OpenCV matcher.
 */
@Slf4j
@Configuration
public class VisionConfig {

    @Bean
    public FaceMatcher faceMatcher(OpenCvSupport cv, ProctoringProperties props) {
        String url = props.faceModelUrl();
        if (url != null && !url.isBlank()) {
            try {
                return new DjlFaceMatcher(cv, url);
            } catch (Exception | Error ex) {
                log.warn("Could not load face model ({}); falling back to OpenCV matcher", ex.getMessage());
            }
        }
        return new OpenCvFaceMatcher(cv);
    }
}
