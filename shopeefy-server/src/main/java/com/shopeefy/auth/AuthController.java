package com.shopeefy.auth;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopeefy.auth.AuthDtos.AuthResponse;
import com.shopeefy.auth.AuthDtos.OtpChallengeResponse;
import com.shopeefy.common.ApiException;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.oauth2.OAuth2Providers;
import com.shopeefy.user.UserDto;

/**
 * Public authentication endpoints. All of them sit behind the {@code auth} token bucket
 * (10 requests a minute per client) and, for the cookie ones, the CSRF guard filter.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final SessionService sessions;
    private final RefreshCookies cookies;
    private final OAuth2Providers providers;

    public AuthController(AuthService auth, SessionService sessions, RefreshCookies cookies, OAuth2Providers providers) {
        this.auth = auth;
        this.sessions = sessions;
        this.cookies = cookies;
        this.providers = providers;
    }

    @GetMapping("/providers")
    Map<String, List<OAuth2Providers.Provider>> providers() {
        return Map.of("oauth2", providers.list());
    }

    @PostMapping("/register")
    ResponseEntity<OtpChallengeResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return ResponseEntity.accepted().body(auth.register(req));
    }

    @PostMapping("/register/verify")
    AuthResponse verifyRegistration(@Valid @RequestBody AuthDtos.VerifyRequest req, HttpServletRequest request,
                                    HttpServletResponse response) {
        return signedIn(auth.verifyRegistration(req, ClientInfo.from(request)), response);
    }

    @PostMapping("/login")
    OtpChallengeResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/login/verify")
    AuthResponse verifyLogin(@Valid @RequestBody AuthDtos.VerifyRequest req, HttpServletRequest request,
                             HttpServletResponse response) {
        return signedIn(auth.verifyLogin(req, ClientInfo.from(request)), response);
    }

    @PostMapping("/otp/resend")
    ResponseEntity<OtpChallengeResponse> resend(@Valid @RequestBody AuthDtos.ResendRequest req) {
        return ResponseEntity.accepted().body(auth.resend(req.challengeId()));
    }

    @PostMapping("/refresh")
    AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return signedIn(sessions.rotate(cookies.read(request), ClientInfo.from(request)), response);
        } catch (ApiException e) {
            if (e.status() == HttpStatus.UNAUTHORIZED) {
                cookies.clear(response);
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(cookies.read(request));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    ResponseEntity<OtpChallengeResponse> forgot(@Valid @RequestBody AuthDtos.ForgotRequest req) {
        return ResponseEntity.accepted().body(auth.forgotPassword(req.email()));
    }

    @PostMapping("/password/reset")
    ResponseEntity<Void> reset(@Valid @RequestBody AuthDtos.ResetRequest req, HttpServletResponse response) {
        auth.resetPassword(req);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    /** The access token goes in the body (kept in memory by the SPA); the refresh token only in the cookie. */
    private AuthResponse signedIn(SessionService.Tokens tokens, HttpServletResponse response) {
        cookies.set(response, tokens.refreshToken(), tokens.refreshTtl());
        return new AuthResponse(tokens.accessToken(), "Bearer", tokens.accessTtl().toSeconds(),
                UserDto.of(tokens.user()));
    }
}
