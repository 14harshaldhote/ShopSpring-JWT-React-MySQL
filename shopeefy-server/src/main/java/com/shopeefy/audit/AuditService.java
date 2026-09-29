package com.shopeefy.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopeefy.common.ClientInfo;
import com.shopeefy.common.LogSanitizer;

/**
 * Security audit trail.                                                          [OWASP A09:2025]
 * <ul>
 *   <li>Every event goes to the {@code SECURITY_AUDIT} log (JSON in prod) and to the
 *       {@code security_events} table, with who / what / when / where / outcome.</li>
 *   <li>Rows are hash-chained: {@code hash = SHA-256(prevHash | fields)}. Changing or deleting a
 *       row breaks every later hash, and {@link #verifyChain()} reports the first broken row.</li>
 *   <li>Writes run in their own transaction, so a failed login that rolls back its own work
 *       still leaves its audit record, and an audit failure never breaks the request.</li>
 *   <li>Secrets never enter it: no passwords, OTP codes or tokens; untrusted text is CR/LF-stripped.</li>
 * </ul>
 */
@Service
public class AuditService {

    static final String GENESIS = "0".repeat(64);
    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final SecurityEventRepository repository;
    private final TransactionTemplate newTransaction;
    private final AlertService alerts;
    private final Clock clock;

    public AuditService(SecurityEventRepository repository, PlatformTransactionManager txManager,
                        AlertService alerts, Clock clock) {
        this.repository = repository;
        this.newTransaction = new TransactionTemplate(txManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.alerts = alerts;
        this.clock = clock;
    }

    public void record(SecurityEventType type, Outcome outcome, Long userId, String email, String detail) {
        record(type, outcome, userId, email, detail, ClientInfo.current());
    }

    public void record(SecurityEventType type, Outcome outcome, Long userId, String email, String detail,
                       ClientInfo client) {
        String safeEmail = LogSanitizer.clean(email, 254);
        String safeDetail = LogSanitizer.clean(detail, 500);
        audit.atLevel(type.severity() == Severity.INFO ? org.slf4j.event.Level.INFO : org.slf4j.event.Level.WARN)
                .addKeyValue("event", type)
                .addKeyValue("severity", type.severity())
                .addKeyValue("outcome", outcome)
                .addKeyValue("userId", userId)
                .addKeyValue("email", LogSanitizer.maskEmail(safeEmail))
                .addKeyValue("ip", client.ip())
                .addKeyValue("request", client.request())
                .log("{} {} user={} ip={} {}", type, outcome, userId, client.ip(), safeDetail == null ? "" : safeDetail);
        try {
            SecurityEvent saved = newTransaction.execute(status -> append(type, outcome, userId, safeEmail, safeDetail, client));
            if (saved != null && type.severity().alertsAdmin()) {
                alerts.notifyAdmin(saved);
            }
        } catch (RuntimeException e) {
            // Logging must not take the application down.                    (Logging Cheat Sheet)
            log.error("Could not persist security event {}", type, e);
        }
    }

    private SecurityEvent append(SecurityEventType type, Outcome outcome, Long userId, String email,
                                              String detail, ClientInfo client) {
        String prev = repository.lockChainHead();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String hash = hash(prev, type, outcome, userId, email, client.ip(), client.userAgent(), client.request(),
                detail, now);
        SecurityEvent saved = repository.save(new SecurityEvent(type, outcome, userId, email, client.ip(),
                client.userAgent(), client.request(), detail, now, prev, hash));
        repository.moveChainHead(hash);
        return saved;
    }

    /** Walks the whole trail and recomputes every hash. */
    public ChainVerification verifyChain() {
        String expectedPrev = GENESIS;
        long checked = 0;
        long lastId = 0;
        Slice<SecurityEvent> page;
        do {
            page = repository.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, 500));
            for (SecurityEvent e : page) {
                String recomputed = hash(e.getPrevHash(), e.getType(), e.getOutcome(), e.getUserId(), e.getEmail(),
                        e.getIpAddress(), e.getUserAgent(), e.getRequest(), e.getDetail(), e.getCreatedAt());
                if (!e.getPrevHash().equals(expectedPrev) || !recomputed.equals(e.getHash())) {
                    return new ChainVerification(false, checked, e.getId());
                }
                expectedPrev = e.getHash();
                lastId = e.getId();
                checked++;
            }
        } while (page.hasNext());
        return new ChainVerification(true, checked, null);
    }

    static String hash(String prev, SecurityEventType type, Outcome outcome, Long userId, String email, String ip,
                       String userAgent, String request, String detail, Instant createdAt) {
        String canonical = String.join("|", prev, type.name(), outcome.name(), Objects.toString(userId, ""),
                Objects.toString(email, ""), Objects.toString(ip, ""), Objects.toString(userAgent, ""),
                Objects.toString(request, ""), Objects.toString(detail, ""),
                Long.toString(ChronoUnit.MICROS.between(Instant.EPOCH, createdAt)));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record ChainVerification(boolean valid, long eventsChecked, Long firstBrokenEventId) {
    }
}
