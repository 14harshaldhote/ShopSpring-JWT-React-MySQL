package com.shopeefy.ops;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopeefy.auth.OtpChallengeRepository;
import com.shopeefy.auth.RefreshTokenRepository;
import com.shopeefy.auth.UserSessionRepository;
import com.shopeefy.config.AppProperties;
import com.shopeefy.order.OrderRepository;
import com.shopeefy.order.OrderService;
import com.shopeefy.user.UserRepository;

/**
 * Housekeeping. Expired secrets are deleted rather than kept forever (data minimisation), and
 * unpaid orders give their stock back so nobody can hold inventory hostage by starting
 * checkouts and never paying.                                     [OWASP A04:2025, A06:2025]
 */
@Component
public class CleanupJobs {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobs.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final OtpChallengeRepository challenges;
    private final RefreshTokenRepository refreshTokens;
    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final Duration pendingTimeout;
    private final Clock clock;

    public CleanupJobs(OrderRepository orders, OrderService orderService, OtpChallengeRepository challenges,
                       RefreshTokenRepository refreshTokens, UserSessionRepository sessions, UserRepository users,
                       TransactionTemplate tx, AppProperties props, Clock clock) {
        this.orders = orders;
        this.orderService = orderService;
        this.challenges = challenges;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.users = users;
        this.tx = tx;
        this.pendingTimeout = props.payment().pendingTimeout();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void expireUnpaidOrders() {
        Instant cutoff = clock.instant().minus(pendingTimeout);
        for (Long id : orders.findExpiredPending(cutoff, PageRequest.of(0, 200))) {
            try {
                orderService.expire(id, cutoff);
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", id, e.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void purgeExpired() {
        Instant now = clock.instant();
        tx.executeWithoutResult(status -> {
            int otps = challenges.deleteExpired(now.minus(Duration.ofDays(1)));
            int tokens = refreshTokens.deleteExpired(now.minus(Duration.ofDays(1)));
            int ended = sessions.deleteEnded(now.minus(Duration.ofDays(30)));
            int stale = users.deleteStaleUnverified(now.minus(Duration.ofDays(1)));
            log.info("Cleanup: {} OTP challenges, {} refresh tokens, {} sessions, {} unverified sign-ups removed",
                    otps, tokens, ended, stale);
        });
    }
}
