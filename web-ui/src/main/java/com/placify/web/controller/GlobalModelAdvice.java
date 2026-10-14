package com.placify.web.controller;

import com.placify.web.config.WebProperties;
import com.placify.web.session.SessionService;
import com.placify.web.session.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Values every page template can use. */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final SessionService sessions;
    private final WebProperties props;

    @ModelAttribute("me")
    public UserSession me(HttpServletRequest request) {
        return sessions.current(request).orElse(null);
    }

    @ModelAttribute("wsUrl")
    public String wsUrl() {
        return props.publicWsUrl();
    }

    @ModelAttribute("appName")
    public String appName() {
        return props.appName();
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
