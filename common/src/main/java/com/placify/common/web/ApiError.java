package com.placify.common.web;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The single error response shape used by every CareerPilot service.
 */
public record ApiError(LocalDateTime timestamp,
                       int status,
                       String code,
                       String message,
                       String path,
                       Map<String, String> fieldErrors) {
}
