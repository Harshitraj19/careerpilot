package com.placify.common.client;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient builders for the services.
 * <ul>
 *   <li>{@code @LoadBalanced} builder: service-to-service calls, addressed by Eureka name
 *       (e.g. {@code http://profile-service}). Inject it with the {@code @LoadBalanced} qualifier.</li>
 *   <li>Primary builder: everything else (Spring AI, external APIs). Without it, libraries that
 *       ask for a {@code RestClient.Builder} would get the load-balanced one and fail to reach
 *       real host names.</li>
 * </ul>
 */
@Configuration
public class InternalClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder internalRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    @Primary
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder());
    }
}
