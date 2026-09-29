package com.shopeefy.common;

import org.springframework.http.HttpStatus;

/** An expected failure with a safe, client-facing message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final long retryAfterSeconds;

    public ApiException(HttpStatus status, String detail) {
        this(status, detail, 0);
    }

    private ApiException(HttpStatus status, String detail, long retryAfterSeconds) {
        super(detail);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, detail);
    }

    /**
     * Also used for resources that exist but belong to someone else, so an attacker cannot
     * tell "not yours" from "doesn't exist".                                       [OWASP A01:2025]
     */
    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail);
    }

    public static ApiException tooManyRequests(long retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Try again in " + retryAfterSeconds + " seconds.", retryAfterSeconds);
    }

    public HttpStatus status() {
        return status;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
