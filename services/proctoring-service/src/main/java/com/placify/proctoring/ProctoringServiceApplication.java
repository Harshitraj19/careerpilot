package com.placify.proctoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI exam proctoring in Java: camera frames are analysed with OpenCV
 * (faces, eyes, motion, brightness) and DJL (phone detection), browser
 * events are recorded, and a trust score and evidence report are produced.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
@ConfigurationPropertiesScan
public class ProctoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProctoringServiceApplication.class, args);
    }
}
