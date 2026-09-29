package com.shopeefy.user;

public enum AuthProvider {
    LOCAL, GOOGLE, GITHUB, DEVIDP;

    public static AuthProvider fromRegistrationId(String registrationId) {
        return valueOf(registrationId.toUpperCase(java.util.Locale.ROOT));
    }
}
