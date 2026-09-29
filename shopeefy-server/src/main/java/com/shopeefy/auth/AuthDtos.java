package com.shopeefy.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.shopeefy.user.UserDto;

/**
 * Request and response shapes for /auth. Every field is validated against an allowlist before
 * any business code runs, and records have no setters for extra JSON fields to bind to
 * (no mass assignment).                                      [OWASP A05:2025, REST Security Cheat Sheet]
 */
public final class AuthDtos {

    static final String NAME = "^\\p{L}[\\p{L} .'-]{0,59}$";
    static final String UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = NAME, message = "Use letters only (up to 60).") String firstName,
            @NotBlank @Pattern(regexp = NAME, message = "Use letters only (up to 60).") String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(max = 256) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(max = 256) String password) {
    }

    public record VerifyRequest(
            @NotBlank @Pattern(regexp = UUID) String challengeId,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code) {
    }

    public record ResendRequest(@NotBlank @Pattern(regexp = UUID) String challengeId) {
    }

    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetRequest(
            @NotBlank @Pattern(regexp = UUID) String challengeId,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code,
            @NotNull @Size(max = 256) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotNull @Size(max = 256) String currentPassword,
            @NotNull @Size(max = 256) String newPassword) {
    }

    public record OtpChallengeResponse(String challengeId, Instant expiresAt, long resendAfterSeconds,
                                       String destination, OtpPurpose purpose) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {
    }
}
