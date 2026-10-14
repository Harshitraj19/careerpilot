package com.placify.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Runs code with Judge0 against public and hidden test cases, produces
 * per-test verdicts, checks plagiarism (JPlag) and requests AI code reviews.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
@ConfigurationPropertiesScan
public class SubmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubmissionServiceApplication.class, args);
    }
}
