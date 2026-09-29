package com.shopeefy.oauth2;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.shopeefy.common.ApiException;
import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;
import com.shopeefy.user.AuthProvider;
import com.shopeefy.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Account takeover through social login.                                    [OWASP A07:2025] */
@DisplayName("A07 OAuth2 account linking: takeover and pre-account hijacking")
class OAuth2AccountLinkingTest extends IntegrationTest {

    @Autowired
    OAuth2AccountService accounts;
    @Autowired
    UserRepository users;

    private static ExternalIdentity google(String subject, String email, boolean verified) {
        return new ExternalIdentity(AuthProvider.GOOGLE, subject, email, verified, "Asha", "Kulkarni");
    }

    @Test
    @DisplayName("A provider account whose email isn't verified can't claim an existing account")
    void unverifiedProviderEmailIsRefused() {
        String email = newEmail();
        signUp(email);
        assertThatThrownBy(() -> accounts.resolve(google("sub-" + email, email, false)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Pre-account hijacking: an attacker's unverified sign-up loses its password when the real owner uses Google")
    void preAccountHijacking() {
        String victimEmail = newEmail();
        // The attacker registers the victim's email but can't read the victim's inbox, so never verifies it.
        assertThat(api().post("/auth/register", Map.of("firstName", "Eve", "lastName", "Attacker", "email", victimEmail,
                "password", "orchid velvet summit 42")).status()).isEqualTo(202);

        var user = accounts.resolve(google("google-sub-" + victimEmail, victimEmail, true));
        var stored = users.findById(user.getId()).orElseThrow();
        assertThat(stored.hasPassword()).as("attacker's password wiped").isFalse();
        assertThat(stored.isEmailVerified()).isTrue();

        Api attacker = api();
        assertThat(attacker.post("/auth/login", Map.of("email", victimEmail, "password", "orchid velvet summit 42")).status())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("The provider's immutable subject id, not the email, identifies a returning user")
    void subjectNotEmail() {
        String email = newEmail();
        var first = accounts.resolve(google("stable-subject-" + email, email, true));
        var renamed = accounts.resolve(google("stable-subject-" + email, "changed-" + email, true));
        assertThat(renamed.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("Linking Google to an existing verified account emails the owner")
    void linkingNotifiesOwner() {
        String email = newEmail();
        signUp(email);
        accounts.resolve(google("link-" + email, email, true));
        assertThat(mail.await(email, "sign-in was added")).isNotNull();
    }
}
