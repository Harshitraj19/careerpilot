package com.placify.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Shared web configuration: stable JSON shape for Spring Data pages and
 * scheduling support for background jobs in every service.
 */
@Configuration
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class CommonWebConfig {
}
