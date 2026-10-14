package com.placify.web.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.placify.web.config.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Talks to the API gateway on behalf of the browser. */
@Component
public class GatewayClient {

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public GatewayClient(WebProperties props, ObjectMapper mapper) {
        this.mapper = mapper;
        this.baseUrl = props.gatewayUrl().replaceAll("/+$", "");
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.rest = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /** JSON call; throws {@link GatewayException} for error answers. */
    public JsonNode json(HttpMethod method, String path, Object body, String token, HttpServletRequest request) {
        ResponseEntity<byte[]> r = raw(method, path, body == null ? null : toJson(body),
                body == null ? null : MediaType.APPLICATION_JSON_VALUE, token, request);
        if (!r.getStatusCode().is2xxSuccessful()) {
            throw error(r);
        }
        try {
            byte[] bytes = r.getBody();
            return bytes == null || bytes.length == 0 ? mapper.nullNode() : mapper.readTree(bytes);
        } catch (Exception ex) {
            throw new GatewayException(502, "BAD_RESPONSE", "Unexpected answer from the server", null);
        }
    }

    public <T> T json(HttpMethod method, String path, Object body, String token, HttpServletRequest request, Class<T> type) {
        JsonNode node = json(method, path, body, token, request);
        try {
            return mapper.treeToValue(node, type);
        } catch (Exception ex) {
            throw new GatewayException(502, "BAD_RESPONSE", "Unexpected answer from the server", null);
        }
    }

    /** Pass-through call used by the BFF proxy; never throws for HTTP errors. */
    public ResponseEntity<byte[]> raw(HttpMethod method, String pathAndQuery, byte[] body, String contentType, String token,
                                      HttpServletRequest request) {
        try {
            RestClient.RequestBodySpec spec = rest.method(method)
                    .uri(target(pathAndQuery))
                    .headers(h -> {
                        if (token != null) {
                            h.setBearerAuth(token);
                        }
                        if (contentType != null) {
                            h.set(HttpHeaders.CONTENT_TYPE, contentType);
                        }
                        h.setAccept(List.of(MediaType.ALL));
                        if (request != null) {
                            h.set("X-Forwarded-For", clientIp(request));
                            String ua = request.getHeader(HttpHeaders.USER_AGENT);
                            if (ua != null) {
                                h.set(HttpHeaders.USER_AGENT, ua);
                            }
                        }
                    });
            if (body != null) {
                spec.body(body);
            }
            return spec.exchange((req, res) -> {
                        byte[] bytes = res.getBody().readAllBytes();
                        return ResponseEntity.status(res.getStatusCode()).headers(res.getHeaders()).body(bytes);
                    });
        } catch (Exception ex) {
            String msg = "{\"status\":503,\"code\":\"GATEWAY_UNREACHABLE\",\"message\":\"The CareerPilot server is not reachable. Please try again shortly.\"}";
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON).body(msg.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Paths coming from the browser (BFF proxy) are already URL-encoded, so they are used as-is;
     * encoding them again would turn "%3A" into "%253A". Paths built in code with raw characters
     * (e.g. spaces) are not valid URIs and fall back to normal encoding.
     */
    private URI target(String pathAndQuery) {
        try {
            return URI.create(baseUrl + pathAndQuery);
        } catch (IllegalArgumentException ex) {
            return UriComponentsBuilder.fromUriString(baseUrl + pathAndQuery).encode().build().toUri();
        }
    }

    public GatewayException error(ResponseEntity<byte[]> r) {
        int status = r.getStatusCode().value();
        try {
            JsonNode n = mapper.readTree(r.getBody());
            Map<String, String> fields = new LinkedHashMap<>();
            if (n.hasNonNull("fieldErrors")) {
                n.get("fieldErrors").properties().forEach(e -> fields.put(e.getKey(), e.getValue().asText()));
            }
            return new GatewayException(status, n.path("code").asText("ERROR"),
                    n.path("message").asText("Request failed"), fields);
        } catch (Exception ex) {
            return new GatewayException(status, "ERROR", "Request failed (HTTP " + status + ")", null);
        }
    }

    private byte[] toJson(Object body) {
        try {
            return mapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /** The browser's address. */
    public static String clientIp(HttpServletRequest request) {
        // Not taken from the client's own X-Forwarded-For header, which could be spoofed
        return request.getRemoteAddr();
    }
}
