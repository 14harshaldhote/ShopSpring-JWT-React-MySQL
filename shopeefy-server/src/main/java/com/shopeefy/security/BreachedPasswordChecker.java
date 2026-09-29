package com.shopeefy.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.web.authentication.password.HaveIBeenPwnedRestApiPasswordChecker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shopeefy.config.AppProperties;

/**
 * HaveIBeenPwned "Pwned Passwords" range check with k-anonymity: only the first 5 hex characters
 * of the password's SHA-1 leave the server, never the password or its full hash. The lookup is
 * Spring Security's own {@link HaveIBeenPwnedRestApiPasswordChecker}; this class adds the timeouts
 * and response padding. (SHA-1 is only the protocol's lookup key; passwords are stored with
 * Argon2id, see {@link PasswordConfig}.)                                  [OWASP A07:2025, A04:2025]
 * <p>
 * If the service is unreachable the check is skipped and Spring logs the failure. This is a
 * deliberate fail-open: it's a defence-in-depth check, and the length and common-password rules
 * still apply, so an outage of a third-party API doesn't block sign-ups.     [OWASP A10:2025]
 */
@Component
public class BreachedPasswordChecker {

    static final String RANGE_API = "https://api.pwnedpasswords.com/range/";

    private final boolean enabled;
    private final CompromisedPasswordChecker pwnedPasswords;

    @Autowired
    public BreachedPasswordChecker(AppProperties props, RestClient.Builder builder) {
        this(props.security().password(), builder, RANGE_API);
    }

    BreachedPasswordChecker(AppProperties.Password config, RestClient.Builder builder, String rangeApi) {
        this.enabled = config.breachCheck();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.breachCheckTimeout());
        factory.setReadTimeout(config.breachCheckTimeout());
        var checker = new HaveIBeenPwnedRestApiPasswordChecker();
        // Add-Padding pads every answer with fake entries, so its size doesn't hint at the prefix.
        checker.setRestClient(builder.clone().baseUrl(rangeApi).requestFactory(factory)
                .defaultHeader("Add-Padding", "true").build());
        this.pwnedPasswords = checker;
    }

    public boolean isBreached(String password) {
        return enabled && pwnedPasswords.check(password).isCompromised();
    }
}
