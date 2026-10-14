package com.placify.common.client;

import com.placify.common.security.CurrentUser;
import com.placify.common.security.PlacifySecurityProperties;
import com.placify.common.security.SecurityHeaders;
import com.placify.common.security.UserPrincipal;
import com.placify.common.web.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Helper for calling another service's /internal/** endpoints.
 * Adds the internal secret and passes on the current user's identity.
 */
@Slf4j
@Component
public class InternalClient {

    private final RestClient restClient;
    private final PlacifySecurityProperties properties;

    public InternalClient(@LoadBalanced RestClient.Builder internalRestClientBuilder, PlacifySecurityProperties properties) {
        this.restClient = internalRestClientBuilder.build();
        this.properties = properties;
    }

    public <T> T get(String service, String path, Class<T> type) {
        try {
            return restClient.get().uri(url(service, path)).headers(this::headers).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw translate(service, path, ex);
        } catch (RestClientException ex) {
            throw ApiException.unavailable(service + " is not reachable");
        }
    }

    public <T> T get(String service, String path, ParameterizedTypeReference<T> type) {
        try {
            return restClient.get().uri(url(service, path)).headers(this::headers).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw translate(service, path, ex);
        } catch (RestClientException ex) {
            throw ApiException.unavailable(service + " is not reachable");
        }
    }

    public byte[] getBytes(String service, String path) {
        return get(service, path, byte[].class);
    }

    public <T> T post(String service, String path, Object body, Class<T> type) {
        try {
            return restClient.post().uri(url(service, path)).headers(this::headers)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw translate(service, path, ex);
        } catch (RestClientException ex) {
            throw ApiException.unavailable(service + " is not reachable");
        }
    }

    public <T> T post(String service, String path, Object body, ParameterizedTypeReference<T> type) {
        try {
            return restClient.post().uri(url(service, path)).headers(this::headers)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw translate(service, path, ex);
        } catch (RestClientException ex) {
            throw ApiException.unavailable(service + " is not reachable");
        }
    }

    /** Like {@link #get(String, String, Class)} but returns empty instead of failing. */
    public <T> Optional<T> tryGet(String service, String path, Class<T> type) {
        try {
            return Optional.ofNullable(get(service, path, type));
        } catch (Exception ex) {
            log.warn("Optional call to {}{} failed: {}", service, path, ex.getMessage());
            return Optional.empty();
        }
    }

    public <T> Optional<T> tryGet(String service, String path, ParameterizedTypeReference<T> type) {
        try {
            return Optional.ofNullable(get(service, path, type));
        } catch (Exception ex) {
            log.warn("Optional call to {}{} failed: {}", service, path, ex.getMessage());
            return Optional.empty();
        }
    }

    private String url(String service, String path) {
        return "http://" + service + path;
    }

    private void headers(HttpHeaders h) {
        h.set(SecurityHeaders.INTERNAL_SECRET, properties.getInternalSecret());
        Optional<UserPrincipal> user = CurrentUser.find();
        user.filter(u -> u.role() != null).ifPresent(u -> {
            h.set(SecurityHeaders.USER_ID, u.userId());
            h.set(SecurityHeaders.USER_ROLE, u.role().name());
            if (u.loginId() != null) {
                h.set(SecurityHeaders.USER_LOGIN, URLEncoder.encode(u.loginId(), StandardCharsets.UTF_8));
            }
            if (u.name() != null) {
                h.set(SecurityHeaders.USER_NAME, URLEncoder.encode(u.name(), StandardCharsets.UTF_8));
            }
        });
    }

    private ApiException translate(String service, String path, RestClientResponseException ex) {
        log.warn("Internal call {}{} failed with {}: {}", service, path, ex.getStatusCode(), ex.getResponseBodyAsString());
        int code = ex.getStatusCode().value();
        if (code == 404) {
            return ApiException.notFound("Not found in " + service);
        }
        if (code == 400) {
            return ApiException.badRequest("Invalid request to " + service);
        }
        return ApiException.unavailable(service + " returned an error");
    }

    /** Utility to make path building readable. */
    public static String q(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
