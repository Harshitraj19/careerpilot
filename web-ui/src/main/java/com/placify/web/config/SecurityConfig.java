package com.placify.web.config;

import com.placify.web.gateway.GatewayClient;
import com.placify.web.session.UserSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Page access by role, CSRF protection for forms and the BFF API, strict
 * security headers, logout that also ends the API session, and the forced
 * password change on first login.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Set<String> FIRST_LOGIN_ALLOWED = Set.of("/account/change-password", "/logout", "/error");

    @Bean
    SecurityFilterChain web(HttpSecurity http, GatewayClient gateway, WebProperties props) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // always load the token so the cookie is set
        http
                .csrf(c -> c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .securityContext(c -> c.requireExplicitSave(false))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/login", "/login/**", "/register/**", "/forgot-password", "/reset-password",
                                "/verify/**", "/css/**", "/js/**", "/img/**", "/favicon.ico", "/error",
                                "/actuator/health").permitAll()
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .requestMatchers("/company/**").hasRole("COMPANY")
                        .requestMatchers("/tpo/**").hasAnyRole("TPO", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor((req, res, ex) -> res.sendError(401),
                                PathPatternRequestMatcher.withDefaults().matcher("/bff/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequest.INSTANCE))
                .logout(l -> l.logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout"))
                        .addLogoutHandler((req, res, auth) -> {
                            HttpSession s = req.getSession(false);
                            UserSession user = s == null ? null : (UserSession) s.getAttribute(UserSession.KEY);
                            if (user != null) {
                                gateway.raw(HttpMethod.POST, "/api/auth/logout", null, null, user.getToken(), req);
                            }
                        })
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("PLACIFY_SESSION"))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(csp(props)))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .frameOptions(f -> f.deny())
                        .permissionsPolicyHeader(p -> p.policy("camera=(self), microphone=(self), display-capture=(self), geolocation=()")))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(new FirstLoginFilter(), CsrfCookieFilter.class);
        return http.build();
    }

    private static String csp(WebProperties props) {
        String ws = props.publicWsUrl() == null ? "" : originOf(props.publicWsUrl());
        return "default-src 'self'; "
                + "script-src 'self' https://cdn.jsdelivr.net blob:; "
                + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                + "font-src 'self' https://cdn.jsdelivr.net data:; "
                + "img-src 'self' data: blob:; "
                + "media-src 'self' blob:; "
                + "worker-src 'self' blob:; "
                + "connect-src 'self' https://cdn.jsdelivr.net " + ws + "; "
                + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'";
    }

    /** "ws://host:8080/ws/notifications" -> "ws://host:8080". */
    static String originOf(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return "";
        }
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    /** Matches every request (used as the default entry point). */
    private enum AnyRequest implements org.springframework.security.web.util.matcher.RequestMatcher {
        INSTANCE;

        @Override
        public boolean matches(HttpServletRequest request) {
            return true;
        }
    }

    /** Makes sure the XSRF-TOKEN cookie is written so JavaScript can send it back. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    /** Until the temporary password is changed, every page leads to the change-password form. */
    static final class FirstLoginFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            HttpSession s = request.getSession(false);
            UserSession user = s == null ? null : (UserSession) s.getAttribute(UserSession.KEY);
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean asset = path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/");
            if (user != null && user.isFirstLogin() && !asset && !FIRST_LOGIN_ALLOWED.contains(path)) {
                if (path.startsWith("/bff/")) {
                    response.sendError(403, "Change your temporary password first");
                } else {
                    response.sendRedirect(request.getContextPath() + "/account/change-password");
                }
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
