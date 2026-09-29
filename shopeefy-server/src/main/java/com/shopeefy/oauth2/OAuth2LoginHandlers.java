package com.shopeefy.oauth2;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.auth.RefreshCookies;
import com.shopeefy.auth.SessionService;
import com.shopeefy.common.ApiException;
import com.shopeefy.common.ClientInfo;
import com.shopeefy.config.AppProperties;
import com.shopeefy.mail.MailService;
import com.shopeefy.user.AuthProvider;
import com.shopeefy.user.User;

/**
 * Ends a provider login the same way as a password login: a server-side session and a refresh
 * cookie. No token is ever put in the redirect URL (where it would leak into history, logs and
 * Referer headers); the web app lands on /oauth2/callback and calls /auth/refresh.  [OWASP A07:2025]
 * The short-lived HTTP session that held the OAuth2 state, nonce and PKCE verifier is destroyed.
 */
@Component
public class OAuth2LoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final OAuth2AccountService accounts;
    private final SessionService sessions;
    private final RefreshCookies cookies;
    private final AuditService audit;
    private final MailService mail;
    private final String frontendUrl;

    public OAuth2LoginHandlers(OAuth2AccountService accounts, SessionService sessions, RefreshCookies cookies,
                               AuditService audit, MailService mail, AppProperties props) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cookies = cookies;
        this.audit = audit;
        this.mail = mail;
        this.frontendUrl = props.frontendUrl();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        var token = (OAuth2AuthenticationToken) authentication;
        AuthProvider provider = AuthProvider.fromRegistrationId(token.getAuthorizedClientRegistrationId());
        ExternalIdentity identity = ExternalIdentity.from(provider, token.getPrincipal());
        ClientInfo client = ClientInfo.from(request);
        endHttpSession(request);
        User user;
        try {
            user = accounts.resolve(identity);
        } catch (ApiException e) {
            audit.record(SecurityEventType.OAUTH2_LOGIN_FAILURE, Outcome.BLOCKED, null, identity.email(),
                    "provider=" + provider + " " + e.getMessage(), client);
            response.sendRedirect(frontendUrl + "/login?error=oauth2_email");
            return;
        }
        boolean knownDevice = sessions.isKnownDevice(user, client);
        var tokens = sessions.start(user, "OAUTH2_" + provider, client);
        cookies.set(response, tokens.refreshToken(), tokens.refreshTtl());
        audit.record(SecurityEventType.OAUTH2_LOGIN_SUCCESS, Outcome.SUCCESS, user.getId(), user.getEmail(),
                "provider=" + provider + " newDevice=" + !knownDevice, client);
        if (!knownDevice) {
            mail.sendNewSignIn(user.getEmail(), provider.name(), client.ip(), client.userAgent());
        }
        response.sendRedirect(frontendUrl + "/oauth2/callback");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode() : exception.getClass().getSimpleName();
        audit.record(SecurityEventType.OAUTH2_LOGIN_FAILURE, Outcome.FAILURE, null, null, "error=" + code,
                ClientInfo.from(request));
        endHttpSession(request);
        response.sendRedirect(frontendUrl + "/login?error=oauth2");
    }

    private static void endHttpSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
