package com.shopeefy.mail;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.shopeefy.audit.SecurityEvent;
import com.shopeefy.auth.OtpPurpose;
import com.shopeefy.common.LogSanitizer;
import com.shopeefy.config.AppProperties;

/**
 * Transactional email. Every method is {@code @Async}: the HTTP response never waits for SMTP,
 * so response time can't reveal which branch ran (for example "account exists" vs "new account").
 * Plain-text bodies only, so user-supplied names can't inject HTML.         [OWASP A07:2025]
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final JavaMailSender sender;
    private final String from;
    private final String frontendUrl;

    public MailService(JavaMailSender sender, AppProperties props) {
        this.sender = sender;
        this.from = props.mail().from();
        this.frontendUrl = props.frontendUrl();
    }

    @Async
    public void sendOtp(String to, OtpPurpose purpose, String code, Duration ttl) {
        String action = switch (purpose) {
            case REGISTER -> "finish creating your ShopSpring account";
            case LOGIN -> "sign in to ShopSpring";
            case PASSWORD_RESET -> "reset your ShopSpring password";
        };
        send(to, code + " is your ShopSpring code", """
                Your code to %s is:

                    %s

                It expires in %d minutes and can only be used once.
                ShopSpring will never ask you for this code by phone or chat.
                If you didn't request it, you can ignore this email; your account is safe.
                """.formatted(action, code, ttl.toMinutes()));
    }

    @Async
    public void sendAccountAlreadyExists(String to) {
        send(to, "Someone tried to create an account with your email", """
                Someone tried to create a new ShopSpring account with this email address,
                but you already have one. No new account was created.

                If it was you, sign in instead, or reset your password at %s/login.
                If it wasn't you, no action is needed.
                """.formatted(frontendUrl));
    }

    @Async
    public void sendAccountLocked(String to, Instant until) {
        send(to, "Your ShopSpring account is temporarily locked", """
                We saw several wrong passwords for your account, so password sign-in is paused
                until %s.

                If this wasn't you, reset your password at %s/login. Resetting it with the code
                we email you also lifts the lock.
                """.formatted(TIME.format(until), frontendUrl));
    }

    @Async
    public void sendNewSignIn(String to, String method, String ip, String userAgent) {
        send(to, "New sign-in to your ShopSpring account", """
                Your account was just used to sign in from a new device.

                    Method:  %s
                    IP:      %s
                    Browser: %s

                If this was you, there's nothing to do. If not, open Account > Security at %s,
                sign out the other devices and reset your password.
                """.formatted(method, ip, LogSanitizer.clean(userAgent, 120), frontendUrl));
    }

    @Async
    public void sendSessionCompromised(String to) {
        send(to, "We signed out one of your devices to protect you", """
                A sign-in token for your account was used twice. That usually means it was
                copied from your device. We signed that session out straight away.

                Please reset your password at %s/login and review your devices under
                Account > Security.
                """.formatted(frontendUrl));
    }

    @Async
    public void sendPasswordChanged(String to) {
        send(to, "Your ShopSpring password was changed", """
                Your password was just changed and your other devices were signed out.
                If you didn't do this, reset your password at %s/login right away.
                """.formatted(frontendUrl));
    }

    @Async
    public void sendIdentityLinked(String to, String provider) {
        send(to, provider + " sign-in was added to your ShopSpring account", """
                You can now sign in to ShopSpring with %s.
                If you didn't do this, contact us and reset your password at %s/login.
                """.formatted(provider, frontendUrl));
    }

    @Async
    public void sendSecurityAlert(String to, SecurityEvent event) {
        send(to, "[ShopSpring security] " + event.getSeverity() + " " + event.getType(), """
                Event:    %s (%s)
                Outcome:  %s
                Time:     %s
                User id:  %s
                IP:       %s
                Request:  %s
                Detail:   %s
                Audit id: %d
                """.formatted(event.getType(), event.getSeverity(), event.getOutcome(),
                TIME.format(event.getCreatedAt()), event.getUserId(), event.getIpAddress(), event.getRequest(),
                event.getDetail(), event.getId()));
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("Email '{}' to {} failed: {}", subject.replaceAll("\\d{6}", "******"),
                    LogSanitizer.maskEmail(to), e.getMessage());
        }
    }
}
