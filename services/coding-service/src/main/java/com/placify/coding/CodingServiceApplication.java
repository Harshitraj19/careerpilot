package com.placify.coding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Coding question bank, test cases, adaptive practice (Elo rating),
 * weekly contests with live leaderboards and badges.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
public class CodingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodingServiceApplication.class, args);
    }
}
