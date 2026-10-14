package com.placify.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security rules applied identically in every business service:
 * <ul>
 *   <li>/internal/** only for service-to-service calls (internal secret)</li>
 *   <li>configured public paths and health checks open</li>
 *   <li>everything else needs a user authenticated by the gateway</li>
 *   <li>fine-grained role checks via {@code @PreAuthorize} on controllers</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PlacifySecurityProperties.class)
public class CommonSecurityConfig {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter(PlacifySecurityProperties properties) {
        return new GatewayAuthFilter(properties);
    }

    /**
     * The filter is only used inside the security chain; stop Spring Boot from
     * also registering it as a plain servlet filter (it would run twice).
     */
    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(GatewayAuthFilter filter) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   GatewayAuthFilter gatewayAuthFilter,
                                                   PlacifySecurityProperties properties) throws Exception {
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll();
                    auth.requestMatchers("/internal/**").hasAuthority(UserPrincipal.INTERNAL_AUTHORITY);
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
