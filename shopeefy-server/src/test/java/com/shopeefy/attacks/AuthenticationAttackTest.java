package com.shopeefy.attacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopeefy.support.Api;
import com.shopeefy.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brute force, credential stuffing, account enumeration and OTP attacks.     [OWASP A07:2025]
 * Each test plays the attacker against the real running application.
 */
@DisplayName("A07 Authentication Failures: attacks on login, sign-up and OTP")
class AuthenticationAttackTest extends IntegrationTest {

    @Test
    @DisplayName("Brute force: 5 wrong passwords lock the account; even the right password is then refused; owner is emailed")
    void bruteForceLocksAccount() {
        String email = newEmail();
        signUp(email);
        Api attacker = api();
        for (int i = 0; i < 5; i++) {
            assertThat(attacker.post("/auth/login", Map.of("email", email, "password", "guess number " + i)).status())
                    .isEqualTo(401);
        }
        var withRightPassword = attacker.post("/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(withRightPassword.status()).isEqualTo(401);
        assertThat(withRightPassword.detail()).isEqualTo("Invalid email or password.");
        assertThat(mail.await(email, "temporarily locked")).isNotNull();
        assertThat(jdbc.queryForObject("select locked_until is not null from users where email = ?", Boolean.class, email))
                .isTrue();
    }

    @Test
    @DisplayName("Parallel guessing can't race the lockout counter: every wrong guess is counted")
    void parallelGuessesAreAllCounted() {
        String email = newEmail();
        signUp(email);
        List<CompletableFuture<Integer>> guesses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int n = i;
            guesses.add(CompletableFuture.supplyAsync(() ->
                    api().post("/auth/login", Map.of("email", email, "password", "parallel guess " + n)).status()));
        }
        guesses.forEach(CompletableFuture::join);
        Integer counted = jdbc.queryForObject("select failed_login_attempts from users where email = ?", Integer.class, email);
        assertThat(counted).isEqualTo(4);
    }

    @Test
    @DisplayName("Credential stuffing from one client is throttled by the token bucket (429 + Retry-After)")
    void credentialStuffingIsRateLimited() {
        Api attacker = api();
        List<Integer> statuses = IntStream.range(0, 12)
                .mapToObj(i -> attacker.post("/auth/login", Map.of("email", "victim" + i + "@example.com",
                        "password", "Summer2026!")).status())
                .toList();
        assertThat(statuses.subList(0, 10)).containsOnly(401);
        var blocked = attacker.post("/auth/login", Map.of("email", "victim@example.com", "password", "x"));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.header("Retry-After")).isNotBlank();
        assertThat(eventCount("RATE_LIMITED")).isPositive();
    }

    @Test
    @DisplayName("Email bombing: resend waits 60 s, and one address gets at most 5 codes an hour")
    void otpEmailBombingIsThrottled() throws InterruptedException {
        String email = newEmail();
        signUp(email);                                                                       // code 1
        Api attacker = api();
        var reset = attacker.post("/auth/password/forgot", Map.of("email", email));          // code 2
        assertThat(reset.status()).isEqualTo(202);
        var resend = attacker.post("/auth/otp/resend", Map.of("challengeId", reset.body().path("challengeId").asString()));
        assertThat(resend.status()).as("resend inside the cooldown").isEqualTo(429);
        assertThat(resend.header("Retry-After")).isNotBlank();

        List<Integer> more = IntStream.range(0, 4)
                .mapToObj(i -> attacker.post("/auth/password/forgot", Map.of("email", email)).status())
                .toList();
        assertThat(more).as("codes 3 to 5, then the address's bucket is empty").containsExactly(202, 202, 202, 429);
        Thread.sleep(300);
        assertThat(mail.countTo(email, "code")).isEqualTo(5);
    }

    @Test
    @DisplayName("Account enumeration: unknown email and wrong password look exactly the same")
    void loginDoesNotRevealAccounts() {
        String email = newEmail();
        signUp(email);
        var wrongPassword = api().post("/auth/login", Map.of("email", email, "password", "not the password 1"));
        var unknownEmail = api().post("/auth/login", Map.of("email", newEmail(), "password", "not the password 1"));
        assertThat(wrongPassword.status()).isEqualTo(unknownEmail.status()).isEqualTo(401);
        assertThat(wrongPassword.detail()).isEqualTo(unknownEmail.detail());
    }

    @Test
    @DisplayName("Account enumeration: sign-up and password reset answer the same for existing and new emails")
    void signUpAndResetDoNotRevealAccounts() {
        String existing = newEmail();
        signUp(existing);
        var again = api().post("/auth/register", Map.of("firstName", "Eve", "lastName", "Attacker",
                "email", existing, "password", "another strong pass 7"));
        var fresh = api().post("/auth/register", Map.of("firstName", "Eve", "lastName", "Attacker",
                "email", newEmail(), "password", "another strong pass 7"));
        assertThat(again.status()).isEqualTo(fresh.status()).isEqualTo(202);
        assertThat(again.body().propertyNames()).isEqualTo(fresh.body().propertyNames());
        assertThat(mail.await(existing, "tried to create an account")).isNotNull();

        var resetKnown = api().post("/auth/password/forgot", Map.of("email", existing));
        var resetUnknown = api().post("/auth/password/forgot", Map.of("email", newEmail()));
        assertThat(resetKnown.status()).isEqualTo(resetUnknown.status()).isEqualTo(202);
        assertThat(resetKnown.body().propertyNames()).isEqualTo(resetUnknown.body().propertyNames());
    }

    @Test
    @DisplayName("OTP brute force: 5 wrong codes burn the challenge, the right code no longer works")
    void otpBruteForceBurnsChallenge() {
        String email = newEmail();
        signUp(email);
        mail.clear();
        Api api = api();
        String challengeId = api.post("/auth/login", Map.of("email", email, "password", PASSWORD))
                .body().path("challengeId").asString();
        String realCode = mail.awaitCode(email);
        String wrong = realCode.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThat(api.post("/auth/login/verify", Map.of("challengeId", challengeId, "code", wrong)).status())
                    .isEqualTo(400);
        }
        var late = api.post("/auth/login/verify", Map.of("challengeId", challengeId, "code", realCode));
        assertThat(late.status()).isEqualTo(400);
        assertThat(late.detail()).contains("no longer valid");
        assertThat(eventCount("OTP_EXHAUSTED")).isPositive();
    }

    @Test
    @DisplayName("OTP replay: a used code can't be used again")
    void otpReplayIsRejected() {
        String email = newEmail();
        signUp(email);
        mail.clear();
        Api api = api();
        String challengeId = api.post("/auth/login", Map.of("email", email, "password", PASSWORD))
                .body().path("challengeId").asString();
        String code = mail.awaitCode(email);
        assertThat(api.post("/auth/login/verify", Map.of("challengeId", challengeId, "code", code)).status()).isEqualTo(200);
        assertThat(api().post("/auth/login/verify", Map.of("challengeId", challengeId, "code", code)).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("OTP race: 8 parallel requests with the right code create exactly one session")
    void otpRaceHasOneWinner() {
        String email = newEmail();
        signUp(email);
        mail.clear();
        String challengeId = api().post("/auth/login", Map.of("email", email, "password", PASSWORD))
                .body().path("challengeId").asString();
        String code = mail.awaitCode(email);
        rateLimiter.reset();   // the race itself is what's under test here, not the rate limit
        List<CompletableFuture<Integer>> attempts = IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                        api().post("/auth/login/verify", Map.of("challengeId", challengeId, "code", code)).status()))
                .toList();
        List<Integer> statuses = attempts.stream().map(CompletableFuture::join).toList();
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
    }

    @Test
    @DisplayName("A sign-up code can't be used to finish a login (purpose is bound to the challenge)")
    void otpPurposeIsBound() {
        String email = newEmail();
        Api api = api();
        String challengeId = api.post("/auth/register", Map.of("firstName", "Asha", "lastName", "K",
                "email", email, "password", PASSWORD)).body().path("challengeId").asString();
        String code = mail.awaitCode(email);
        assertThat(api.post("/auth/login/verify", Map.of("challengeId", challengeId, "code", code)).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("Weak, common and personal passwords are refused at sign-up")
    void passwordPolicy() {
        String email = newEmail();
        for (String weak : List.of("short1", "password123", "qwertyuiop", "aaaaaaaaaaaa", email.split("@")[0] + "2026!")) {
            var res = api().post("/auth/register", Map.of("firstName", "Asha", "lastName", "Kulkarni",
                    "email", email, "password", weak));
            assertThat(res.status()).as(weak).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("Password reset works while locked, lifts the lock and signs out every device")
    void resetUnlocksAndRevokesSessions() {
        String email = newEmail();
        Api victim = signUp(email);
        for (int i = 0; i < 5; i++) {
            api().post("/auth/login", Map.of("email", email, "password", "wrong guess " + i));
        }
        mail.clear();
        String challengeId = api().post("/auth/password/forgot", Map.of("email", email)).body().path("challengeId").asString();
        var reset = api().post("/auth/password/reset", Map.of("challengeId", challengeId, "code", mail.awaitCode(email),
                "newPassword", "a brand new long passphrase 5"));
        assertThat(reset.status()).as(reset.toString()).isEqualTo(204);
        assertThat(victim.post("/auth/refresh", null).status()).isEqualTo(401);
        rateLimiter.reset();
        signIn(email, "a brand new long passphrase 5");
    }
}
