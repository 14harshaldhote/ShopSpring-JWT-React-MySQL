package com.shopeefy.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast configuration check for the prod profile: the app refuses to start with dev
 * defaults, missing secrets, the mock payment gateway or API docs switched on.   [OWASP A02:2025]
 */
@Component
public class StartupSecurityValidator implements InitializingBean {

    private final AppProperties props;
    private final Environment env;
    private final SecretResolver secrets;

    public StartupSecurityValidator(AppProperties props, Environment env, SecretResolver secrets) {
        this.props = props;
        this.env = env;
        this.secrets = secrets;
    }

    @Override
    public void afterPropertiesSet() {
        if (!secrets.isProduction()) {
            return;
        }
        List<String> problems = new ArrayList<>();
        var security = props.security();
        if (isBlank(security.jwt().privateKey())) {
            problems.add("JWT_PRIVATE_KEY is not set");
        }
        if (isBlank(security.otp().pepper()) || security.otp().pepper().length() < 32) {
            problems.add("OTP_PEPPER must be at least 32 characters");
        }
        if (!security.refresh().cookieSecure()) {
            problems.add("COOKIE_SECURE must be true");
        }
        if ("dev-app-password".equals(env.getProperty("spring.datasource.password"))) {
            problems.add("DB_PASSWORD still has the development default");
        }
        if (props.payment().isMock()) {
            problems.add("PAYMENT_PROVIDER=mock is for local development only");
        } else if (isBlank(props.payment().razorpay().keySecret()) || isBlank(props.payment().razorpay().webhookSecret())) {
            problems.add("RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET must be set");
        }
        if (props.allowedOrigins().stream().anyMatch(o -> !o.startsWith("https://"))) {
            problems.add("ALLOWED_ORIGINS must only contain https:// origins");
        }
        if (env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
            problems.add("Swagger UI must be disabled");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with an insecure production configuration: " + problems);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
