package com.shopeefy.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Positive (allow-list) validation of every field: length, character set and format.
 *                                                                      [OWASP A05:2025, REST cheat sheet]
 */
public record AddressRequest(
        Long addressId,
        @Size(max = 60) @Pattern(regexp = "^[\\p{L} .'-]*$", message = "letters only") String firstName,
        @Size(max = 60) @Pattern(regexp = "^[\\p{L} .'-]*$", message = "letters only") String lastName,
        @Size(max = 255) String streetAddress,
        @Size(max = 80) @Pattern(regexp = "^[\\p{L} .'-]*$", message = "letters only") String city,
        @Size(max = 80) @Pattern(regexp = "^[\\p{L} .'-]*$", message = "letters only") String state,
        @Size(max = 12) @Pattern(regexp = "^[0-9A-Za-z -]*$", message = "invalid postal code") String zipCode,
        @Size(max = 20) @Pattern(regexp = "^\\+?[0-9 ]{7,19}$", message = "invalid phone number") String mobile) {

    public boolean isComplete() {
        return notBlank(firstName) && notBlank(lastName) && notBlank(streetAddress) && notBlank(city)
                && notBlank(state) && notBlank(zipCode) && notBlank(mobile);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Requires either an existing address id or a complete new address. */
    @AssertTrue(message = "either addressId or a complete address is required")
    public boolean isValidChoice() {
        return addressId != null || isComplete();
    }
}
