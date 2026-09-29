package com.shopeefy.security;

import java.util.HashSet;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopeefy.audit.AuditService;
import com.shopeefy.common.ProblemWriter;
import com.shopeefy.config.AppProperties;

/**
 * Filters that run before Spring Security (whose chain sits at order -100), cheapest first:
 * body size cap, then the token-bucket rate limiter, then the CSRF guard for cookie endpoints.
 *                                                                   [OWASP A01:2025, A07:2025, A10:2025]
 */
@Configuration
public class FilterConfig {

    static final long MAX_BODY_BYTES = 64 * 1024;

    @Bean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(ProblemWriter problems) {
        var bean = new FilterRegistrationBean<>(new RequestSizeLimitFilter(MAX_BODY_BYTES, problems));
        bean.setOrder(-120);
        return bean;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, IpBlocklist blocklist,
                                                            ProblemWriter problems, AuditService audit) {
        var bean = new FilterRegistrationBean<>(new RateLimitFilter(limiter, blocklist, problems, audit));
        bean.setOrder(-110);
        return bean;
    }

    @Bean
    FilterRegistrationBean<CookieRequestGuardFilter> cookieRequestGuardFilter(AppProperties props,
                                                                              ProblemWriter problems,
                                                                              AuditService audit) {
        var bean = new FilterRegistrationBean<>(
                new CookieRequestGuardFilter(new HashSet<>(props.allowedOrigins()), problems, audit));
        bean.setOrder(-105);
        return bean;
    }
}
