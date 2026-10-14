package com.placify.web.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void websocketOriginForContentSecurityPolicy() {
        assertThat(SecurityConfig.originOf("ws://localhost:8080/ws/notifications")).isEqualTo("ws://localhost:8080");
        assertThat(SecurityConfig.originOf("wss://placify.example.com")).isEqualTo("wss://placify.example.com");
        assertThat(SecurityConfig.originOf("not a url")).isEmpty();
    }
}
