package com.shopeefy.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.shopeefy.user.Address;

/** A copy of the address taken when the order is placed, so later address edits don't rewrite history. */
@Embeddable
public class ShippingAddress {

    @Column(name = "ship_first_name")
    private String firstName;
    @Column(name = "ship_last_name")
    private String lastName;
    @Column(name = "ship_street_address")
    private String streetAddress;
    @Column(name = "ship_city")
    private String city;
    @Column(name = "ship_state")
    private String state;
    @Column(name = "ship_zip_code")
    private String zipCode;
    @Column(name = "ship_mobile")
    private String mobile;

    protected ShippingAddress() {
    }

    static ShippingAddress copyOf(Address a) {
        ShippingAddress s = new ShippingAddress();
        s.firstName = a.getFirstName();
        s.lastName = a.getLastName();
        s.streetAddress = a.getStreetAddress();
        s.city = a.getCity();
        s.state = a.getState();
        s.zipCode = a.getZipCode();
        s.mobile = a.getMobile();
        return s;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getStreetAddress() { return streetAddress; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getZipCode() { return zipCode; }
    public String getMobile() { return mobile; }
}
