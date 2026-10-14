package com.placify.auth.service;

import com.placify.auth.config.AuthProperties;
import com.placify.auth.domain.CompanySignup;
import com.placify.auth.domain.OtpCode;
import com.placify.auth.domain.User;
import com.placify.auth.domain.UserStatus;
import com.placify.auth.repository.CompanySignupRepository;
import com.placify.auth.repository.LoginHistoryRepository;
import com.placify.auth.repository.UserRepository;
import com.placify.auth.security.SessionService;
import com.placify.auth.web.AuthDtos.AuthStats;
import com.placify.auth.web.AuthDtos.CompanyRegisterRequest;
import com.placify.auth.web.AuthDtos.CreateAdminRequest;
import com.placify.auth.web.AuthDtos.CreateStudentRequest;
import com.placify.auth.web.AuthDtos.CreateTpoRequest;
import com.placify.auth.web.AuthDtos.CreatedAccount;
import com.placify.auth.web.AuthDtos.StudentRegisterRequest;
import com.placify.auth.web.AuthDtos.ImportReport;
import com.placify.auth.web.AuthDtos.PendingCompanyView;
import com.placify.common.audit.Audited;
import com.placify.common.messaging.EventPublisher;
import com.placify.common.messaging.Events;
import com.placify.common.messaging.RoutingKeys;
import com.placify.common.security.CurrentUser;
import com.placify.common.security.Role;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Account administration: student import, TPO/admin creation, company
 * registration and approval, blocking and unblocking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final CompanySignupRepository signups;
    private final LoginHistoryRepository history;
    private final PasswordEncoder encoder;
    private final TempPasswordGenerator passwords;
    private final StudentImportParser parser;
    private final SessionService sessions;
    private final OtpService otpService;
    private final CaptchaService captcha;
    private final EventPublisher publisher;
    private final AuthProperties props;

    // ---------------------------------------------------------------- students

    @Audited(action = "STUDENTS_IMPORTED")
    @Transactional
    public ImportReport importStudents(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read upload");
        }
        List<StudentImportParser.StudentRow> rows = parser.parse(file.getOriginalFilename(), bytes);
        List<CreatedAccount> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        int skipped = 0;

        for (StudentImportParser.StudentRow row : rows) {
            String problem = validate(row);
            String regNo = row.regNo().trim().toUpperCase();
            if (problem == null && !seenInFile.add(regNo)) {
                problem = "duplicate Reg No in file";
            }
            if (problem == null && users.existsByLoginId(regNo)) {
                problem = "account already exists";
            }
            if (problem != null) {
                skipped++;
                errors.add("Row " + row.rowNumber() + " (" + row.regNo() + "): " + problem);
                continue;
            }
            BigDecimal cgpa = row.cgpa().isBlank() ? null : new BigDecimal(row.cgpa());
            created.add(createStudentAccount(regNo, row.name(), row.email(), row.branch(),
                    Integer.parseInt(row.batch().trim()), cgpa, row.phone()));
        }
        return new ImportReport(rows.size(), created.size(), skipped, created, errors);
    }

    @Audited(action = "STUDENT_CREATED", target = "#req.regNo()")
    @Transactional
    public CreatedAccount createStudent(CreateStudentRequest req) {
        String regNo = req.regNo().trim().toUpperCase();
        if (users.existsByLoginId(regNo)) {
            throw ApiException.conflict("A student with this registration number already exists");
        }
        return createStudentAccount(regNo, req.name(), req.email(), req.branch(), req.batch(), req.cgpa(), req.phone());
    }

    private String validate(StudentImportParser.StudentRow row) {
        if (row.regNo().isBlank()) {
            return "Reg No is empty";
        }
        if (row.name().isBlank()) {
            return "Name is empty";
        }
        if (!EMAIL.matcher(row.email()).matches()) {
            return "invalid email";
        }
        if (row.branch().isBlank()) {
            return "Branch is empty";
        }
        try {
            int batch = Integer.parseInt(row.batch().trim());
            if (batch < 2000 || batch > 2100) {
                return "invalid batch year";
            }
        } catch (NumberFormatException ex) {
            return "invalid batch year";
        }
        if (!row.cgpa().isBlank()) {
            try {
                BigDecimal cgpa = new BigDecimal(row.cgpa());
                if (cgpa.compareTo(BigDecimal.ZERO) < 0 || cgpa.compareTo(BigDecimal.TEN) > 0) {
                    return "CGPA must be between 0 and 10";
                }
            } catch (NumberFormatException ex) {
                return "invalid CGPA";
            }
        }
        return null;
    }

    private CreatedAccount createStudentAccount(String regNo, String name, String email, String branch,
                                                int batch, BigDecimal cgpa, String phone) {
        String temp = passwords.generate();
        User user = users.save(User.builder()
                .loginId(regNo)
                .email(email.trim().toLowerCase())
                .name(name.trim())
                .phone(phone == null || phone.isBlank() ? null : phone.trim())
                .passwordHash(encoder.encode(temp))
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .firstLogin(true)
                .build());

        publisher.publish(RoutingKeys.STUDENT_IMPORTED, new Events.StudentImportedEvent(
                user.getId(), regNo, user.getName(), user.getEmail(), branch.trim(), batch, cgpa, user.getPhone()));
        sendCredentials(user, temp, "Student portal (login with your registration number)");
        return new CreatedAccount(user.getId(), regNo, user.getName(), user.getEmail(),
                props.exposeTempPasswords() ? temp : null);
    }

    // ---------------------------------------------------------------- staff

    @Audited(action = "TPO_CREATED", target = "#req.employeeId()")
    @Transactional
    public CreatedAccount createTpo(CreateTpoRequest req) {
        String employeeId = req.employeeId().trim().toUpperCase();
        if (users.existsByLoginId(employeeId)) {
            throw ApiException.conflict("An account with this employee ID already exists");
        }
        String temp = passwords.generate();
        User user = users.save(User.builder()
                .loginId(employeeId)
                .email(req.email().trim().toLowerCase())
                .name(req.name().trim())
                .phone(req.phone())
                .passwordHash(encoder.encode(temp))
                .role(Role.TPO)
                .status(UserStatus.ACTIVE)
                .firstLogin(true)
                .build());
        sendCredentials(user, temp, "TPO portal (login with your employee ID)");
        return new CreatedAccount(user.getId(), employeeId, user.getName(), user.getEmail(),
                props.exposeTempPasswords() ? temp : null);
    }

    @Audited(action = "ADMIN_CREATED", target = "#req.username()")
    @Transactional
    public CreatedAccount createAdmin(CreateAdminRequest req) {
        String username = req.username().trim().toLowerCase();
        if (users.existsByLoginId(username)) {
            throw ApiException.conflict("An account with this username already exists");
        }
        String temp = passwords.generate();
        User user = users.save(User.builder()
                .loginId(username)
                .email(req.email().trim().toLowerCase())
                .name(req.name().trim())
                .passwordHash(encoder.encode(temp))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .firstLogin(true)
                .build());
        sendCredentials(user, temp, "Admin portal");
        return new CreatedAccount(user.getId(), username, user.getName(), user.getEmail(),
                props.exposeTempPasswords() ? temp : null);
    }

    private void sendCredentials(User user, String tempPassword, String portalText) {
        String message = "Hello " + user.getName() + ",\n\nYour CareerPilot account has been created.\n"
                + "Portal: " + portalText + "\nLogin ID: " + user.getLoginId()
                + "\nTemporary password: " + tempPassword
                + "\n\nYou will be asked to set a new password when you first log in.";
        publisher.notify(new Events.NotificationEvent(List.of(), "Your CareerPilot account", message, "ACCOUNT", null,
                true, List.of(user.getEmail()), "Your CareerPilot account is ready"));
    }

    // ---------------------------------------------------------------- companies

    /** @return the code that was sent (shown on screen only in demo mode) */
    @Transactional
    public String sendCompanyOtp(String email) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByLoginId(normalized)) {
            throw ApiException.conflict("This email is already registered");
        }
        return otpService.send(normalized, OtpCode.Purpose.COMPANY_REGISTRATION, "registering your company");
    }

    // ---------------------------------------------------------------- student self-registration

    /** Step 1: checks the registration number and e-mail are free and e-mails a code. */
    @Transactional
    public String sendStudentOtp(String regNo, String email) {
        requireStudentSelfRegistration();
        String normalizedEmail = email.trim().toLowerCase();
        checkStudentAvailable(regNo.trim().toUpperCase(), normalizedEmail);
        return otpService.send(normalizedEmail, OtpCode.Purpose.STUDENT_REGISTRATION, "creating your student account");
    }

    /**
     * Step 2: creates an active student account with the student's own password. The profile is
     * created from the same event as for students added by the placement office.
     */
    @Audited(action = "STUDENT_SELF_REGISTERED", target = "#req.regNo()")
    @Transactional
    public CreatedAccount registerStudent(StudentRegisterRequest req) {
        requireStudentSelfRegistration();
        captcha.verify(req.captchaId(), req.captchaAnswer());
        String regNo = req.regNo().trim().toUpperCase();
        String email = req.email().trim().toLowerCase();
        checkStudentAvailable(regNo, email);
        otpService.verify(email, OtpCode.Purpose.STUDENT_REGISTRATION, req.otp());

        User user = users.save(User.builder()
                .loginId(regNo)
                .email(email)
                .name(req.name().trim())
                .phone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim())
                .passwordHash(encoder.encode(req.password()))
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .firstLogin(false)
                .build());
        publisher.publish(RoutingKeys.STUDENT_IMPORTED, new Events.StudentImportedEvent(
                user.getId(), regNo, user.getName(), email, req.branch().trim().toUpperCase(), req.batch(), req.cgpa(), user.getPhone()));
        List<String> staff = users.findByRoleIn(List.of(Role.TPO)).stream().map(User::getId).toList();
        publisher.notifyUsers(staff, "New student registration",
                user.getName() + " (" + regNo + ", " + req.branch() + ") created a student account.", "STUDENT_REGISTERED",
                "/tpo/students", false);
        return new CreatedAccount(user.getId(), regNo, user.getName(), email, null);
    }

    private void requireStudentSelfRegistration() {
        if (!props.studentSelfRegistration()) {
            throw ApiException.forbidden("Student sign-up is turned off. Please contact the placement office for your account.");
        }
    }

    private void checkStudentAvailable(String regNo, String email) {
        if (users.existsByLoginId(regNo)) {
            throw ApiException.conflict("An account with this registration number already exists. Use \"Forgot password\" if it is yours.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("This e-mail is already used by another account");
        }
    }

    @Transactional
    public void registerCompany(CompanyRegisterRequest req) {
        captcha.verify(req.captchaId(), req.captchaAnswer());
        String email = req.email().trim().toLowerCase();
        if (users.existsByLoginId(email)) {
            throw ApiException.conflict("This email is already registered");
        }
        otpService.verify(email, OtpCode.Purpose.COMPANY_REGISTRATION, req.otp());

        User user = users.save(User.builder()
                .loginId(email)
                .email(email)
                .name(req.companyName().trim())
                .phone(req.phone())
                .passwordHash(encoder.encode(req.password()))
                .role(Role.COMPANY)
                .status(UserStatus.PENDING)
                .firstLogin(false)
                .build());
        signups.save(CompanySignup.builder()
                .userId(user.getId())
                .companyName(req.companyName().trim())
                .website(req.website())
                .industry(req.industry())
                .contactPerson(req.contactPerson())
                .build());

        List<String> staff = users.findByRoleIn(List.of(Role.TPO, Role.ADMIN)).stream().map(User::getId).toList();
        publisher.notifyUsers(staff, "New company registration",
                req.companyName() + " has registered and is waiting for approval.", "COMPANY_APPROVAL",
                "/tpo/companies", false);
        publisher.notify(Events.NotificationEvent.emailOnly(email, "Registration received",
                "Thank you for registering " + req.companyName() + " on CareerPilot. The Training & Placement Office "
                        + "will review your registration. You will receive an email once it is approved."));
    }

    public List<PendingCompanyView> pendingCompanies() {
        return users.findByRoleAndStatus(Role.COMPANY, UserStatus.PENDING).stream()
                .map(u -> {
                    CompanySignup s = signups.findById(u.getId()).orElse(null);
                    return new PendingCompanyView(u.getId(),
                            s == null ? u.getName() : s.getCompanyName(),
                            u.getEmail(),
                            s == null ? null : s.getWebsite(),
                            s == null ? null : s.getIndustry(),
                            s == null ? null : s.getContactPerson(),
                            u.getPhone(),
                            u.getCreatedAt());
                })
                .toList();
    }

    @Audited(action = "COMPANY_APPROVED", target = "#companyUserId")
    @Transactional
    public void approveCompany(String companyUserId) {
        User company = findCompany(companyUserId);
        if (company.getStatus() == UserStatus.ACTIVE) {
            throw ApiException.badRequest("Company is already approved");
        }
        company.setStatus(UserStatus.ACTIVE);
        company.setStatusReason(null);
        CompanySignup s = signups.findById(companyUserId).orElse(null);
        publisher.publish(RoutingKeys.COMPANY_APPROVED, new Events.CompanyApprovedEvent(
                company.getId(),
                s == null ? company.getName() : s.getCompanyName(),
                company.getEmail(),
                s == null ? null : s.getWebsite(),
                s == null ? null : s.getIndustry()));
        publisher.notifyUser(company.getId(), "Registration approved",
                "Your company account on CareerPilot has been approved. You can now log in and post jobs.",
                "ACCOUNT", "/company/dashboard", true);
    }

    @Audited(action = "COMPANY_REJECTED", target = "#companyUserId")
    @Transactional
    public void rejectCompany(String companyUserId, String reason) {
        User company = findCompany(companyUserId);
        company.setStatus(UserStatus.REJECTED);
        company.setStatusReason(reason);
        publisher.notify(Events.NotificationEvent.emailOnly(company.getEmail(), "Registration not approved",
                "Your company registration on CareerPilot was not approved."
                        + (reason == null || reason.isBlank() ? "" : " Reason: " + reason)));
    }

    private User findCompany(String id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Company not found"));
        if (u.getRole() != Role.COMPANY) {
            throw ApiException.badRequest("User is not a company");
        }
        return u;
    }

    // ---------------------------------------------------------------- block / unblock

    @Audited(action = "USER_BLOCKED", target = "#userId")
    @Transactional
    public void block(String userId, String reason) {
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        checkCanManage(target);
        target.setStatus(UserStatus.BLOCKED);
        target.setStatusReason(reason);
        sessions.end(userId);
    }

    @Audited(action = "USER_UNBLOCKED", target = "#userId")
    @Transactional
    public void unblock(String userId) {
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        checkCanManage(target);
        if (target.getStatus() == UserStatus.BLOCKED) {
            target.setStatus(UserStatus.ACTIVE);
            target.setStatusReason(null);
        }
        target.setFailedAttempts(0);
        target.setLockedUntil(null);
    }

    /** TPOs manage students and companies; only admins manage TPOs and admins. */
    private void checkCanManage(User target) {
        UserPrincipal me = CurrentUser.get();
        if (me.userId().equals(target.getId())) {
            throw ApiException.badRequest("You cannot change your own account status");
        }
        if (me.role() == Role.TPO && (target.getRole() == Role.TPO || target.getRole() == Role.ADMIN)) {
            throw ApiException.forbidden("Only an admin can manage staff accounts");
        }
    }

    // ---------------------------------------------------------------- stats

    public AuthStats stats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long blocked = 0;
        for (Role r : Role.values()) {
            blocked += users.countByRoleAndStatus(r, UserStatus.BLOCKED);
        }
        return new AuthStats(
                users.countByRole(Role.STUDENT),
                users.countByRoleAndStatus(Role.COMPANY, UserStatus.ACTIVE),
                users.countByRoleAndStatus(Role.COMPANY, UserStatus.PENDING),
                users.countByRole(Role.TPO),
                users.countByRole(Role.ADMIN),
                blocked,
                history.countByCreatedAtAfterAndSuccess(weekAgo, true),
                history.countByCreatedAtAfterAndSuccess(weekAgo, false));
    }
}
