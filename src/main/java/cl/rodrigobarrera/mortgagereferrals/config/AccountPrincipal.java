package cl.rodrigobarrera.mortgagereferrals.config;

import cl.rodrigobarrera.mortgagereferrals.model.UserAccount;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class AccountPrincipal extends User {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final byte[] credentialFingerprint;

    AccountPrincipal(UserAccount account) {
        super(account.getEmail(), account.getPasswordHash(), account.isActive(), true, true, true,
            AuthorityUtils.createAuthorityList("ROLE_" + account.getRole().getName()));
        accountId = account.getId();
        // Spring erases the login password hash after authenticating the account.
        credentialFingerprint = fingerprint(account.getPasswordHash());
    }

    public long accountId() {
        return accountId;
    }

    boolean hasCurrentCredentials(UserAccount account) {
        return getUsername().equals(account.getEmail())
            && MessageDigest.isEqual(credentialFingerprint, fingerprint(account.getPasswordHash()));
    }

    private static byte[] fingerprint(String hash) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(hash.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El entorno Java no ofrece SHA-256.", e);
        }
    }
}
