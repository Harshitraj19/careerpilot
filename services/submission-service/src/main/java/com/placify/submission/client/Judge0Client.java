package com.placify.submission.client;

import com.placify.common.web.ApiException;
import com.placify.submission.config.Judge0Properties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP client for Judge0 (sandboxed code execution). Supports both a
 * self-hosted Judge0 (X-Auth-Token) and the RapidAPI hosted version.
 * Protected by a circuit breaker so a Judge0 outage fails fast.
 * <p>
 * When no Judge0 is configured (or {@code CODE_RUNNER=local}), code is compiled and run on this
 * machine by {@link LocalCodeRunner}; tokens are then answered from memory.
 */
@Slf4j
@Component
public class Judge0Client {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private static final String LOCAL_PREFIX = "local-";

    private final RestClient client;
    private final Judge0Properties props;
    private final LocalCodeRunner localRunner;
    private final Map<String, Map<String, Object>> localResults = new ConcurrentHashMap<>();

    public Judge0Client(Judge0Properties props, LocalCodeRunner localRunner) {
        this.props = props;
        this.localRunner = localRunner;
        log.info("Code execution: {}", props.useLocalRunner() ? "local runner on this machine" : "Judge0 at " + props.baseUrl());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        // plain builder (not the load-balanced one): Judge0 is an external host
        this.client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeaders(this::authHeaders)
                .build();
    }

    private void authHeaders(HttpHeaders h) {
        h.setContentType(MediaType.APPLICATION_JSON);
        if (props.useRapidApi()) {
            h.set("X-RapidAPI-Key", props.rapidApiKey());
            h.set("X-RapidAPI-Host", props.rapidApiHost());
        }
        if (props.authToken() != null && !props.authToken().isBlank()) {
            h.set("X-Auth-Token", props.authToken());
        }
    }

    public boolean isLocal() {
        return props.useLocalRunner();
    }

    /** Queues a submission and returns its token. */
    @CircuitBreaker(name = "judge0")
    public String create(Map<String, Object> payload) {
        if (props.useLocalRunner()) {
            return runLocally(payload);
        }
        Map<String, Object> body = client.post()
                .uri("/submissions?base64_encoded=false&wait=false")
                .body(payload)
                .retrieve()
                .body(MAP);
        Object token = body == null ? null : body.get("token");
        if (token == null) {
            throw ApiException.unavailable("Judge0 did not return a token");
        }
        return token.toString();
    }

    @CircuitBreaker(name = "judge0")
    public Map<String, Object> fetch(String token) {
        if (token.startsWith(LOCAL_PREFIX)) {
            return localResults.remove(token);
        }
        return client.get()
                .uri("/submissions/{token}?base64_encoded=false&fields=token,stdout,stderr,compile_output,message,status,time,memory", token)
                .retrieve()
                .body(MAP);
    }

    /** "Run code" with custom input: submit, then poll briefly until finished. */
    public Map<String, Object> runAndWait(Map<String, Object> payload) {
        String token = create(payload);
        Map<String, Object> result = null;
        for (int i = 0; i < props.runMaxPolls(); i++) {
            sleep(props.runPollDelayMs());
            result = fetch(token);
            Integer status = statusId(result);
            if (status != null && status > 2) {
                return result;
            }
        }
        return result;
    }

    private String runLocally(Map<String, Object> payload) {
        int languageId = ((Number) payload.get("language_id")).intValue();
        Object expected = payload.get("expected_output");
        Object limit = payload.get("cpu_time_limit");
        Map<String, Object> result = localRunner.run(languageId, (String) payload.get("source_code"),
                (String) payload.get("stdin"), expected == null ? null : expected.toString(),
                limit instanceof Number n ? n.doubleValue() : null);
        String token = LOCAL_PREFIX + UUID.randomUUID();
        localResults.put(token, result);
        return token;
    }

    public static Integer statusId(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object status = body.get("status");
        if (status instanceof Map<?, ?> m && m.get("id") instanceof Number n) {
            return n.intValue();
        }
        if (body.get("status_id") instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    public static String statusDescription(Map<String, Object> body) {
        if (body != null && body.get("status") instanceof Map<?, ?> m && m.get("description") != null) {
            return m.get("description").toString();
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
