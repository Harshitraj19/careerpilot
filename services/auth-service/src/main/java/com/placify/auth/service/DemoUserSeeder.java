package com.placify.auth.service;

import com.placify.auth.domain.User;
import com.placify.auth.domain.UserStatus;
import com.placify.auth.repository.UserRepository;
import com.placify.common.demo.DemoData;
import com.placify.common.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo accounts (DEMO_DATA=true): six students, two companies and a placement officer. */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = DemoData.PROPERTY, havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // each demo account is created only if missing, so new demo users reach existing databases too
        int created = 0;
        String studentHash = null;
        for (DemoData.Student s : DemoData.STUDENTS) {
            if (!users.existsById(s.id())) {
                studentHash = studentHash == null ? encoder.encode(DemoData.STUDENT_PASSWORD) : studentHash;
                users.save(user(s.id(), s.regNo(), s.email(), s.name(), s.phone(), Role.STUDENT, studentHash));
                created++;
            }
        }
        String companyHash = null;
        for (DemoData.Company c : DemoData.COMPANIES) {
            if (!users.existsById(c.id())) {
                companyHash = companyHash == null ? encoder.encode(DemoData.COMPANY_PASSWORD) : companyHash;
                users.save(user(c.id(), c.email(), c.email(), c.name(), null, Role.COMPANY, companyHash));
                created++;
            }
        }
        if (!users.existsById(DemoData.TPO_ID)) {
            users.save(user(DemoData.TPO_ID, DemoData.TPO_EMPLOYEE_ID, "tpo@placify.local", "Dr. Priya Raman", "9000000300",
                    Role.TPO, encoder.encode(DemoData.TPO_PASSWORD)));
            created++;
        }
        if (created > 0) {
            log.info("{} demo accounts created: students 22BCE1001.. / {}, companies hr@technova.demo .. / {}, TPO {} / {}",
                    created, DemoData.STUDENT_PASSWORD, DemoData.COMPANY_PASSWORD, DemoData.TPO_EMPLOYEE_ID, DemoData.TPO_PASSWORD);
        }
    }

    private static User user(String id, String login, String email, String name, String phone, Role role, String hash) {
        return User.builder().id(id).loginId(login).email(email).name(name).phone(phone).role(role)
                .status(UserStatus.ACTIVE).firstLogin(false).passwordHash(hash).build();
    }
}
