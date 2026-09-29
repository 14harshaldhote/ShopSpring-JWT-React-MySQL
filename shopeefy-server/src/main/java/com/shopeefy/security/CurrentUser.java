package com.shopeefy.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the caller from the verified token. The user id always comes from the token, never from the request body. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public static String sessionId(Jwt jwt) {
        return jwt.getClaimAsString("sid");
    }
}
