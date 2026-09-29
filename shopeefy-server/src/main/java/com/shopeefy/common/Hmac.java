package com.shopeefy.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 helpers for payment and webhook signatures.                     [OWASP A08:2025] */
public final class Hmac {

    private Hmac() {
    }

    public static String sha256Hex(String key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String key, String data) {
        return sha256Hex(key, data.getBytes(StandardCharsets.UTF_8));
    }

    /** Constant-time comparison, so response timing doesn't reveal how many leading characters matched. */
    public static boolean matches(String expectedHex, String presented) {
        if (expectedHex == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                presented.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}
