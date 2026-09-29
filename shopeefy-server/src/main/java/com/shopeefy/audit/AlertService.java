package com.shopeefy.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shopeefy.config.AppProperties;
import com.shopeefy.mail.MailService;
import com.shopeefy.security.RateLimiter;

/**
 * Real-time alerting for HIGH and CRITICAL events (token theft, account lockouts, forged payments,
 * honeytoken hits). Alerts go to the security mailbox, throttled by their own token bucket so an
 * attack can't turn into a mail flood.                                          [OWASP A09:2025]
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_ALERT");

    private final MailService mail;
    private final RateLimiter rateLimiter;
    private final String adminEmail;

    public AlertService(MailService mail, RateLimiter rateLimiter, AppProperties props) {
        this.mail = mail;
        this.rateLimiter = rateLimiter;
        this.adminEmail = props.security().alerts().adminEmail();
    }

    void notifyAdmin(SecurityEvent event) {
        log.warn("ALERT {} {} event={} ip={}", event.getSeverity(), event.getType(), event.getId(), event.getIpAddress());
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        if (rateLimiter.tryConsume("alert-mail", "admin").allowed()) {
            mail.sendSecurityAlert(adminEmail, event);
        }
    }
}
