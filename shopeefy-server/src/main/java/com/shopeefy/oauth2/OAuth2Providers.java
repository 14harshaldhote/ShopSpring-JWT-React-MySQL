package com.shopeefy.oauth2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/** The sign-in buttons the web app should show: only providers that are actually configured. */
@Component
public class OAuth2Providers {

    private final ConfiguredClientRegistrations registrations;

    public OAuth2Providers(ConfiguredClientRegistrations registrations) {
        this.registrations = registrations;
    }

    public List<Provider> list() {
        List<Provider> list = new ArrayList<>();
        registrations.forEach(r -> list.add(new Provider(r.getRegistrationId(), r.getClientName(),
                "/oauth2/authorization/" + r.getRegistrationId())));
        return list;
    }

    public record Provider(String id, String name, String url) {
    }
}
