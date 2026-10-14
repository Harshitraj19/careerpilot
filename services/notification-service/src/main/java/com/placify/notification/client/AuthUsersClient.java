package com.placify.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.placify.common.client.InternalClient;
import com.placify.common.client.ServiceNames;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Looks up users (names, e-mails, everyone with a role) in the auth service. */
@Component
@RequiredArgsConstructor
public class AuthUsersClient {

    private final InternalClient client;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BasicUser(String id, String loginId, String name, String email, String role, String status) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    public List<BasicUser> byRole(String role) {
        return client.get(ServiceNames.AUTH, "/internal/auth/users?role=" + InternalClient.q(role),
                new ParameterizedTypeReference<List<BasicUser>>() {
                });
    }

    public List<BasicUser> batch(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<BasicUser> out = new ArrayList<>();
        // keep requests reasonably small
        for (int i = 0; i < ids.size(); i += 500) {
            out.addAll(client.post(ServiceNames.AUTH, "/internal/auth/users/batch",
                    Map.of("ids", ids.subList(i, Math.min(ids.size(), i + 500))),
                    new ParameterizedTypeReference<List<BasicUser>>() {
                    }));
        }
        return out;
    }
}
