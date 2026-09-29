package com.shopeefy.oauth2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeefy.audit.AuditService;
import com.shopeefy.audit.Outcome;
import com.shopeefy.audit.SecurityEventType;
import com.shopeefy.common.ApiException;
import com.shopeefy.mail.MailService;
import com.shopeefy.user.User;
import com.shopeefy.user.UserIdentity;
import com.shopeefy.user.UserIdentityRepository;
import com.shopeefy.user.UserRepository;

/**
 * Maps a provider login to a shop account.                                   [OWASP A07:2025]
 * <ol>
 *   <li>Known identity (provider + subject id): sign in that account. Subject ids never change,
 *       unlike emails, so a changed email at the provider can't move the login to another account.</li>
 *   <li>New identity: the provider must say the email is verified, or we refuse. Otherwise anyone
 *       could add an unverified victim@example.com at some provider and take over the account.</li>
 *   <li>Existing verified account with that email: link and email the owner.</li>
 *   <li>Existing unverified account: the password on it was set by someone who never proved the
 *       mailbox, maybe an attacker who pre-registered the victim's email hoping they would later
 *       sign in with Google ("pre-account hijacking"). The password is wiped before linking.</li>
 * </ol>
 */
@Service
public class OAuth2AccountService {

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final AuditService audit;
    private final MailService mail;

    public OAuth2AccountService(UserRepository users, UserIdentityRepository identities, AuditService audit,
                                MailService mail) {
        this.users = users;
        this.identities = identities;
        this.audit = audit;
        this.mail = mail;
    }

    @Transactional
    public User resolve(ExternalIdentity id) {
        var known = identities.findWithUser(id.provider(), id.subject());
        if (known.isPresent()) {
            return known.get().getUser();
        }
        if (id.email() == null || !id.emailVerified()) {
            throw ApiException.forbidden("Your " + id.provider() + " account has no verified email address.");
        }
        User user = users.findByEmailForUpdate(id.email()).orElse(null);
        if (user == null) {
            user = new User(id.email(), cut(id.firstName()), cut(id.lastName()));
            user.setAuthProvider(id.provider());
            user.setEmailVerified(true);
            users.save(user);
        } else {
            String detail = "provider=" + id.provider();
            if (!user.isEmailVerified()) {
                user.setPasswordHash(null);
                user.setEmailVerified(true);
                user.setFirstName(cut(id.firstName()));
                user.setLastName(cut(id.lastName()));
                detail += " unverifiedPasswordWiped=true";
            }
            audit.record(SecurityEventType.OAUTH2_ACCOUNT_LINKED, Outcome.SUCCESS, user.getId(), user.getEmail(), detail);
            mail.sendIdentityLinked(user.getEmail(), id.provider().name().charAt(0)
                    + id.provider().name().substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        identities.save(new UserIdentity(user, id.provider(), id.subject(), id.email()));
        return user;
    }

    private static String cut(String value) {
        return value.length() > 60 ? value.substring(0, 60) : value;
    }
}
