package com.placify.interview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Interview rounds (company evaluations with a structured scorecard),
 * interview experiences shared by seniors (TPO moderated) and the
 * company-wise Prep Hub with an AI summary.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }
}
