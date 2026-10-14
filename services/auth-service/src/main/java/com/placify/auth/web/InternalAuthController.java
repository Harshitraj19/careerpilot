package com.placify.auth.web;

import com.placify.auth.domain.UserStatus;
import com.placify.auth.repository.UserRepository;
import com.placify.auth.service.UserAdminService;
import com.placify.auth.web.AuthDtos.AuthStats;
import com.placify.auth.web.AuthDtos.BasicUser;
import com.placify.auth.web.AuthDtos.UserIdsRequest;
import com.placify.common.security.Role;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service endpoints (never exposed through the gateway).
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final UserRepository users;
    private final UserAdminService adminService;

    @GetMapping("/users/{id}")
    public BasicUser user(@PathVariable String id) {
        return users.findById(id).map(BasicUser::of).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @PostMapping("/users/batch")
    public List<BasicUser> batch(@RequestBody UserIdsRequest req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            return List.of();
        }
        return users.findByIdIn(req.ids()).stream().map(BasicUser::of).toList();
    }

    @GetMapping("/users")
    public List<BasicUser> byRole(@RequestParam Role role) {
        return users.findByRoleAndStatus(role, UserStatus.ACTIVE).stream().map(BasicUser::of).toList();
    }

    @GetMapping("/stats")
    public AuthStats stats() {
        return adminService.stats();
    }
}
