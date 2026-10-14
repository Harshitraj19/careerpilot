package com.placify.placement.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Student data returned by profile-service (/internal/profile/students/...).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StudentInfo(
        String userId,
        String regNo,
        String name,
        String email,
        String branch,
        Integer batch,
        BigDecimal cgpa,
        BigDecimal tenthPercentage,
        BigDecimal twelfthPercentage,
        int backlogs,
        List<String> skills,
        int completeness,
        boolean hasResume,
        boolean hasPhoto,
        Integer latestAtsScore,
        Integer bestAtsScore) {
}
