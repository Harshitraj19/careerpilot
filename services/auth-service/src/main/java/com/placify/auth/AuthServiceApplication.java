package com.placify.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Login and account management: role portals, captcha, lockout, OTP, 2FA,
 * bulk student import, company approval and login history.
 */
@SpringBootApplication(scanBasePackages = "com.placify")
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
