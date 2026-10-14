package com.placify.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aptitude MCQ bank and practice, timed tests (aptitude, coding, mixed) linked
 * to job rounds, per-student randomisation and automatic grading.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
public class AssessmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmentServiceApplication.class, args);
    }
}
