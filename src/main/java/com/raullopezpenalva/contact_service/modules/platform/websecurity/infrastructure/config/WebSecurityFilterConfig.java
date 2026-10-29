package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.config;

import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.filter.ContactRateLimitFilter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSecurityFilterConfig {

    @Bean
    public FilterRegistrationBean<ContactRateLimitFilter> contactRateLimitFilterRegistration(
        ContactRateLimitFilter contactRateLimitFilter
    ) {
        FilterRegistrationBean<ContactRateLimitFilter> registration = new FilterRegistrationBean<>();
        
        registration.setFilter(contactRateLimitFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(-200);

        return registration;
    }
}
