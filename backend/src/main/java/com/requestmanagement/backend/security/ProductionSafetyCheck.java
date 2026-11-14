package com.requestmanagement.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Production start-up guard for the demo accounts that Flyway V2/V3 seed with a password published
 * in this repository. When {@code masar.production=true}, the application refuses to start while any
 * ACTIVE account still uses a seed password hash or the seed password, unless the operator explicitly
 * sets {@code masar.security.allow-seed-accounts=true} (then it logs a loud error instead). Fix it by
 * deactivating the seed accounts or giving them new passwords. Development and tests ({@code masar.production=false}) keep
 * their fixtures and are not checked.
 */
@Component
@Slf4j
public class ProductionSafetyCheck implements ApplicationRunner {
    /** Hashes written by V3__hash_seed_user_passwords.sql (all for the published seed password). */
    static final Set<String> SEED_HASHES = Set.of(
            "$2a$10$D6GkgpH60O.XvS98mYXDMO8.nuVXrEsRiWw1e0VlgyrvYGUB37X8e",
            "$2a$10$jtk8BdebpSRfr.SO.ZeI.e7bsD2AijpmvUjvpBGpZXK6UUcknUN.i",
            "$2a$10$AmAjJa/72SVmjFq7di391ub81i6KevJsH3QyUHRUwLt6sEEhMgNG6");
    /** Accounts created by V2__insert_initial_data.sql. */
    static final Set<String> SEED_EMAILS = Set.of("sara.saad@example.com", "nora.ahmed@example.com", "nouf.khaled@example.com");
    private static final String SEED_PASSWORD = "Password123";

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final boolean production;
    private final boolean allowSeedAccounts;

    public ProductionSafetyCheck(NamedParameterJdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                                 @Value("${masar.production:false}") boolean production,
                                 @Value("${masar.security.allow-seed-accounts:false}") boolean allowSeedAccounts) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.production = production;
        this.allowSeedAccounts = allowSeedAccounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!production) return;
        List<String> risky = activeSeedAccounts();
        if (risky.isEmpty()) {
            log.info("Production safety check passed: no active account uses the published seed password.");
            return;
        }
        String message = "SECURITY: " + risky.size() + " active account(s) still use the published seed password: "
                + String.join(", ", risky) + ". Deactivate these accounts or give them new passwords.";
        if (allowSeedAccounts) {
            log.error("\n**********************************************************************\n{}\n"
                    + "Starting anyway because MASAR_ALLOW_SEED_ACCOUNTS=true. Do not leave it like this.\n"
                    + "**********************************************************************", message);
            return;
        }
        throw new IllegalStateException(message + " Refusing to start in production mode.");
    }

    /** Emails of ACTIVE accounts that still use a seed hash or (for the seed emails) the seed password. */
    List<String> activeSeedAccounts() {
        var params = new MapSqlParameterSource("hashes", SEED_HASHES).addValue("emails", SEED_EMAILS);
        List<String[]> candidates = jdbc.query("""
                SELECT email, password_hash FROM users
                 WHERE is_active AND (password_hash IN (:hashes) OR lower(email) IN (:emails))
                 ORDER BY email
                """, params, (rs, row) -> new String[] {rs.getString("email"), rs.getString("password_hash")});
        List<String> risky = new ArrayList<>();
        for (String[] candidate : candidates) {
            if (SEED_HASHES.contains(candidate[1]) || matchesSeedPassword(candidate[1])) risky.add(candidate[0]);
        }
        return risky;
    }

    private boolean matchesSeedPassword(String hash) {
        try {
            return passwordEncoder.matches(SEED_PASSWORD, hash);
        } catch (RuntimeException exception) {
            return false; // not a BCrypt hash (e.g. replaced with an unusable value)
        }
    }
}
