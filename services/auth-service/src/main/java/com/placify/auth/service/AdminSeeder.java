package com.placify.auth.service;

import com.placify.auth.config.AuthProperties;
import com.placify.auth.domain.User;
import com.placify.auth.domain.UserStatus;
import com.placify.auth.repository.UserRepository;
import com.placify.common.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the super-admin account on first startup (when no admin exists).
 * The admin must change the configured password on first login.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        users.save(User.builder()
                .loginId(props.admin().username().trim().toLowerCase())
                .email(props.admin().email())
                .name("Super Admin")
                .passwordHash(encoder.encode(props.admin().password()))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .firstLogin(true)
                .build());
        log.info("Created super-admin account '{}' (password must be changed at first login)", props.admin().username());
    }
}
