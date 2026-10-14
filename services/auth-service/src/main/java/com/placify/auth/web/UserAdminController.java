package com.placify.auth.web;

import com.placify.auth.domain.UserStatus;
import com.placify.auth.repository.LoginHistoryRepository;
import com.placify.auth.repository.UserRepository;
import com.placify.auth.service.UserAdminService;
import com.placify.auth.web.AuthDtos.CreateAdminRequest;
import com.placify.auth.web.AuthDtos.CreateStudentRequest;
import com.placify.auth.web.AuthDtos.CreateTpoRequest;
import com.placify.auth.web.AuthDtos.CreatedAccount;
import com.placify.auth.web.AuthDtos.ImportReport;
import com.placify.auth.web.AuthDtos.LoginHistoryView;
import com.placify.auth.web.AuthDtos.PendingCompanyView;
import com.placify.auth.web.AuthDtos.StatusRequest;
import com.placify.auth.web.AuthDtos.UserView;
import com.placify.common.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Account administration for TPOs and admins.
 */
@Tag(name = "Account administration")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService service;
    private final UserRepository users;
    private final LoginHistoryRepository history;

    @Operation(summary = "Bulk-create student accounts from a CSV or Excel file")
    @PostMapping(value = "/students/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public ImportReport importStudents(@RequestPart("file") MultipartFile file) {
        return service.importStudents(file);
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public CreatedAccount createStudent(@Valid @RequestBody CreateStudentRequest req) {
        return service.createStudent(req);
    }

    @Operation(summary = "Create a TPO account (admin only)")
    @PostMapping("/admin/tpo")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedAccount createTpo(@Valid @RequestBody CreateTpoRequest req) {
        return service.createTpo(req);
    }

    @PostMapping("/admin/admins")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedAccount createAdmin(@Valid @RequestBody CreateAdminRequest req) {
        return service.createAdmin(req);
    }

    @Operation(summary = "Companies waiting for approval")
    @GetMapping("/companies/pending")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public List<PendingCompanyView> pending() {
        return service.pendingCompanies();
    }

    @PostMapping("/companies/{id}/approve")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public void approve(@PathVariable String id) {
        service.approveCompany(id);
    }

    @PostMapping("/companies/{id}/reject")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public void reject(@PathVariable String id, @RequestBody(required = false) StatusRequest req) {
        service.rejectCompany(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Search users")
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public Page<UserView> search(@RequestParam(required = false) Role role,
                                 @RequestParam(required = false) UserStatus status,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return users.search(role, status, query, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()))
                .map(UserView::of);
    }

    @PostMapping("/users/{id}/block")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public void block(@PathVariable String id, @RequestBody(required = false) StatusRequest req) {
        service.block(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Unblock a user and clear any lockout")
    @PostMapping("/users/{id}/unblock")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public void unblock(@PathVariable String id) {
        service.unblock(id);
    }

    @Operation(summary = "All login attempts (admin)")
    @GetMapping("/admin/login-history")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<LoginHistoryView> allHistory(@RequestParam(required = false) String userId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 200));
        return (userId == null || userId.isBlank()
                ? history.findAllByOrderByCreatedAtDesc(pr)
                : history.findByUserIdOrderByCreatedAtDesc(userId, pr))
                .map(LoginHistoryView::of);
    }
}
