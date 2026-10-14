package com.placify.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * placify.security.* settings shared by all services.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "placify.security")
public class PlacifySecurityProperties {

    /** Secret the gateway attaches to every forwarded request. */
    private String gatewaySecret = "change-me-gateway-secret";

    /** Secret used for service-to-service calls on /internal/** endpoints. */
    private String internalSecret = "change-me-internal-secret";

    /** Extra paths (Ant patterns) that do not need a logged-in user. */
    private List<String> publicPaths = new ArrayList<>();
}
