package com.shopeefy.user;

public record AddressDto(Long id, String firstName, String lastName, String streetAddress, String city,
                         String state, String zipCode, String mobile) {

    public static AddressDto of(Address a) {
        return new AddressDto(a.getId(), a.getFirstName(), a.getLastName(), a.getStreetAddress(), a.getCity(),
                a.getState(), a.getZipCode(), a.getMobile());
    }
}
