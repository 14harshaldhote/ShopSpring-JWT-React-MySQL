package com.shopeefy.user;

import java.time.Instant;

/** The only shape in which a user leaves the API: no hash, no lockout state.  [OWASP A01:2025] */
public record UserDto(Long id, String email, String firstName, String lastName, String mobile, Role role,
                      AuthProvider authProvider, boolean emailVerified, Instant createdAt) {

    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getMobile(), u.getRole(),
                u.getAuthProvider(), u.isEmailVerified(), u.getCreatedAt());
    }
}
