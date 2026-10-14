package com.placify.profile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Student and company profiles, resume upload and parsing, ATS resume scoring.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
    }
}
