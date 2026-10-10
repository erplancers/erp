package com.erplancers.erp.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies Jackson serialization for analytics manifests in installations that
 * do not include a web starter with Jackson auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
public class AnalyticsJacksonConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper analyticsObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
