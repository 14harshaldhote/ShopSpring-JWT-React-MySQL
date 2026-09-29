package com.shopeefy.account;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.audit.SecurityEventRepository;
import com.shopeefy.auth.AuthDtos;
import com.shopeefy.auth.AuthService;
import com.shopeefy.auth.SessionService;
import com.shopeefy.auth.UserSession;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ApiException;
import com.shopeefy.security.CurrentUser;
import com.shopeefy.user.AddressDto;
import com.shopeefy.user.AddressRepository;
import com.shopeefy.user.UserDto;
import com.shopeefy.user.UserRepository;

/**
 * The signed-in user's own account. Every query is keyed by the user id from the verified token,
 * never by an id from the URL or body, so there is no way to reach another user's data (IDOR).
 *                                                                              [OWASP A01:2025]
 */
@RestController
@Validated
@RequestMapping("/api/users")
public class AccountController {

    private final UserRepository users;
    private final AddressRepository addresses;
    private final SessionService sessions;
    private final AuthService auth;
    private final SecurityEventRepository events;

    public AccountController(UserRepository users, AddressRepository addresses, SessionService sessions,
                             AuthService auth, SecurityEventRepository events) {
        this.users = users;
        this.addresses = addresses;
        this.sessions = sessions;
        this.auth = auth;
        this.events = events;
    }

    @GetMapping({"/me", "/profile"})
    UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(CurrentUser.id(jwt)).map(UserDto::of)
                .orElseThrow(() -> ApiException.unauthorized("Sign in again."));
    }

    /** Signed-in devices, so users can spot and cut off a session they don't recognise. */
    @GetMapping("/me/sessions")
    List<SessionView> sessions(@AuthenticationPrincipal Jwt jwt) {
        String current = CurrentUser.sessionId(jwt);
        return sessions.listActive(CurrentUser.id(jwt)).stream()
                .map(s -> SessionView.of(s, s.getId().equals(current))).toList();
    }

    @DeleteMapping("/me/sessions/{id}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable @Pattern(regexp = "^[0-9a-f-]{36}$") String id) {
        sessions.revoke(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/sessions/revoke-others")
    ResponseEntity<Void> revokeOthers(@AuthenticationPrincipal Jwt jwt) {
        sessions.revokeAll(CurrentUser.id(jwt), "USER_REVOKED_OTHERS", CurrentUser.sessionId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody AuthDtos.ChangePasswordRequest req) {
        auth.changePassword(CurrentUser.id(jwt), CurrentUser.sessionId(jwt), req);
        return ResponseEntity.noContent().build();
    }

    /** The user's own recent security activity: sign-ins, failures, lockouts, resets. [OWASP A09:2025] */
    @GetMapping("/me/security-events")
    List<EventView> securityEvents(@AuthenticationPrincipal Jwt jwt) {
        return events.findTop20ByUserIdOrderByIdDesc(CurrentUser.id(jwt)).stream()
                .map(e -> new EventView(e.getType(), e.getIpAddress(), e.getUserAgent(), e.getDetail(), e.getCreatedAt()))
                .toList();
    }

    @GetMapping("/me/addresses")
    @Transactional(readOnly = true)
    List<AddressDto> addresses(@AuthenticationPrincipal Jwt jwt) {
        return addresses.findByUserIdOrderByCreatedAtDesc(CurrentUser.id(jwt)).stream().map(AddressDto::of).toList();
    }

    public record SessionView(String id, String authMethod, Instant createdAt, Instant lastUsedAt, Instant expiresAt,
                              String userAgent, String ipAddress, boolean current) {
        static SessionView of(UserSession s, boolean current) {
            return new SessionView(s.getId(), s.getAuthMethod(), s.getCreatedAt(), s.getLastUsedAt(), s.getExpiresAt(),
                    s.getUserAgent(), s.getIpAddress(), current);
        }
    }

    public record EventView(SecurityEventType type, String ipAddress, String userAgent, String detail,
                            Instant createdAt) {
    }
}
