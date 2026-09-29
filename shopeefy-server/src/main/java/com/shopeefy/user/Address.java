package com.shopeefy.user;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String firstName;
    private String lastName;
    private String streetAddress;
    private String city;
    private String state;
    private String zipCode;
    private String mobile;
    private Instant createdAt = Instant.now();

    protected Address() {
    }

    public Address(User user, AddressRequest req) {
        this.user = user;
        this.firstName = req.firstName().strip();
        this.lastName = req.lastName().strip();
        this.streetAddress = req.streetAddress().strip();
        this.city = req.city().strip();
        this.state = req.state().strip();
        this.zipCode = req.zipCode().strip();
        this.mobile = req.mobile().strip();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getStreetAddress() { return streetAddress; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getZipCode() { return zipCode; }
    public String getMobile() { return mobile; }
}
