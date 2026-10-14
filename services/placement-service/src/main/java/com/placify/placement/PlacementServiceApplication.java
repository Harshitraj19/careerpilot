package com.placify.placement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The placement workflow: jobs, eligibility and policy rules, applications,
 * multi-round selection, matching, interview slots, offers and certificates.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
public class PlacementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlacementServiceApplication.class, args);
    }
}
