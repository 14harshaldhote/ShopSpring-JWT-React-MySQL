package com.shopeefy.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.shopeefy.config.AppProperties;
import com.shopeefy.user.User;

/**
 * Per-account lockout against brute force and password spraying.   [OWASP A07:2025, Authentication Cheat Sheet]
 * <ul>
 *   <li>The failure counter belongs to the account, not the IP, so rotating IPs doesn't help.</li>
 *   <li>5 failures inside a 15-minute observation window lock password sign-in.</li>
 *   <li>Lock time grows exponentially with each lockout (1, 2, 4, 8 ... minutes, capped at 1 hour)
 *       instead of a fixed or permanent lock, which would let anyone deny service to a victim.</li>
 *   <li>Password reset by email code still works while locked, and it lifts the lock.</li>
 * </ul>
 */
@Component
public class LoginProtection {

    private final AppProperties.Lockout config;
    private final Clock clock;

    public LoginProtection(AppProperties props, Clock clock) {
        this.config = props.security().lockout();
        this.clock = clock;
    }

    public boolean isLocked(User user) {
        return user.isLocked(clock.instant());
    }

    /** Records a wrong password. Returns the lock end time if this failure locked the account. */
    public Instant recordFailure(User user) {
        Instant now = clock.instant();
        Instant windowStart = user.getFirstFailedLoginAt();
        if (windowStart == null || windowStart.plus(config.observationWindow()).isBefore(now)) {
            user.setFirstFailedLoginAt(now);
            user.setFailedLoginAttempts(1);
        } else {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        }
        if (user.getFailedLoginAttempts() < config.threshold()) {
            return null;
        }
        int lockouts = user.getLockoutCount() + 1;
        Duration lock = config.baseDuration().multipliedBy(1L << Math.min(lockouts - 1, 20));
        if (lock.compareTo(config.maxDuration()) > 0) {
            lock = config.maxDuration();
        }
        Instant until = now.plus(lock);
        user.setLockoutCount(lockouts);
        user.setLockedUntil(until);
        user.setFailedLoginAttempts(0);
        user.setFirstFailedLoginAt(null);
        return until;
    }

    public void recordSuccess(User user) {
        user.setFailedLoginAttempts(0);
        user.setFirstFailedLoginAt(null);
        user.setLockoutCount(0);
        user.setLockedUntil(null);
    }
}
