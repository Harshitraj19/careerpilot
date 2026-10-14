package com.placify.analytics.web;

import com.placify.analytics.domain.ReadinessSnapshot;
import com.placify.analytics.service.ReadinessService;
import com.placify.analytics.web.AnalyticsDtos.ReadinessScore;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/analytics")
@RequiredArgsConstructor
public class InternalAnalyticsController {

    private final ReadinessService readiness;

    @GetMapping("/readiness/{userId}")
    public ReadinessScore readiness(@PathVariable String userId) {
        ReadinessSnapshot s = readiness.get(userId);
        return new ReadinessScore(userId, s.getScore());
    }
}
