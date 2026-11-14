package com.requestmanagement.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production start-up check against the published seed accounts. The test database keeps its
 * seed fixtures; every change here is rolled back.
 */
@SpringBootTest
@Transactional
class ProductionSafetyCheckIntegrationTests {
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProductionSafetyCheck contextCheck;

    private ProductionSafetyCheck check(boolean production, boolean allow) {
        return new ProductionSafetyCheck(namedJdbc, passwordEncoder, production, allow);
    }

    @Test
    void developmentAndTestsAreNotChecked() {
        assertThatCode(() -> contextCheck.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThatCode(() -> check(false, false).run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void productionRefusesToStartWhileSeedAccountsAreActiveUnlessExplicitlyAllowed() {
        assertThat(check(true, false).activeSeedAccounts()).contains("sara.saad@example.com", "nouf.khaled@example.com");
        assertThatThrownBy(() -> check(true, false).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sara.saad@example.com")
                .hasMessageContaining("Deactivate these accounts");
        assertThatCode(() -> check(true, true).run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void productionStartsOnceSeedAccountsAreDeactivatedOrRehashed() {
        // The fix an operator applies: seed accounts inactive, and any other account
        // that copied a seed hash gets a new password.
        jdbc.update("UPDATE users SET is_active=false WHERE email IN ('sara.saad@example.com','nouf.khaled@example.com')");
        jdbc.update("UPDATE users SET password_hash=? WHERE email='nora.ahmed@example.com'", passwordEncoder.encode("A-new-long-password-2026"));
        String[] seedHashes = ProductionSafetyCheck.SEED_HASHES.toArray(String[]::new);
        jdbc.update("UPDATE users SET password_hash=? WHERE is_active AND password_hash IN (?,?,?)",
                passwordEncoder.encode("Another-long-password-2026"), seedHashes[0], seedHashes[1], seedHashes[2]);

        assertThat(check(true, false).activeSeedAccounts()).isEmpty();
        assertThatCode(() -> check(true, false).run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void aSeedEmailWithTheSeedPasswordUnderANewHashIsStillDetected() {
        jdbc.update("UPDATE users SET password_hash=? WHERE email='nora.ahmed@example.com'", passwordEncoder.encode("Password123"));
        assertThat(check(true, false).activeSeedAccounts()).contains("nora.ahmed@example.com");
    }
}
