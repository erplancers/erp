package com.erplancers.erp.common.config;

import gg.jte.TemplateEngine;
import gg.jte.springframework.boot.autoconfigure.JteProperties;
import gg.jte.springframework.boot.autoconfigure.JteViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JteMvcConfiguration {

    @Bean
    public JteViewResolver erpJteViewResolver(TemplateEngine templateEngine, JteProperties jteProperties) {
        return new JteViewResolver(templateEngine, jteProperties);
    }
}
