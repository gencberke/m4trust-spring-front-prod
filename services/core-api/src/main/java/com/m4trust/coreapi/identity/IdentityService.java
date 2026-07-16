package com.m4trust.coreapi.identity;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.m4trust.coreapi.organization.TenantProvisioningPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012345",
            "letmeinletmein",
            "password123456",
            "passwordpassword",
            "qwertyuiopasdfg");

    private final String dummyPasswordHash;
    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TenantProvisioningPort tenantProvisioning;

    IdentityService(IdentityRepository repository, PasswordEncoder passwordEncoder,
            TenantProvisioningPort tenantProvisioning) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tenantProvisioning = tenantProvisioning;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-never-used");
    }

    @Transactional
    public PublicUser register(String email, String password, String displayName) {
        String normalizedEmail = EmailAddress.normalize(email);
        rejectWeakPassword(password);

        IdentityAccount account = new IdentityAccount(
                UUID.randomUUID(), normalizedEmail, passwordEncoder.encode(password),
                displayName.trim(), true);
        try {
            repository.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEmailException();
        }
        tenantProvisioning.provisionForNewUser(account.id());
        return toPublicUser(account);
    }

    public PublicUser authenticate(String email, String password) {
        String normalizedEmail = EmailAddress.normalize(email);
        IdentityAccount account = repository.findByNormalizedEmail(normalizedEmail)
                .orElse(null);
        String hash = account == null ? dummyPasswordHash : account.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(password, hash);
        if (account == null || !account.enabled() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return toPublicUser(account);
    }

    private void rejectWeakPassword(String password) {
        String comparable = password.strip().toLowerCase(Locale.ROOT);
        boolean oneRepeatedCharacter = comparable.codePoints().distinct().limit(2).count() == 1;
        if (COMMON_PASSWORDS.contains(comparable) || oneRepeatedCharacter) {
            throw new WeakPasswordException();
        }
    }

    private PublicUser toPublicUser(IdentityAccount account) {
        return new PublicUser(account.id(), account.email(), account.displayName());
    }
}
