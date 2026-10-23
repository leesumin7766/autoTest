package com.autotest.api_gateway;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Docker service names can resolve to a new address after container recreation. */
@Configuration
public class ContainerDnsConfiguration {
    @Bean
    HttpClientCustomizer containerDnsCache() {
        return client -> client.resolver(spec -> spec
                .cacheMinTimeToLive(Duration.ZERO)
                .cacheMaxTimeToLive(Duration.ofSeconds(5))
                .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
