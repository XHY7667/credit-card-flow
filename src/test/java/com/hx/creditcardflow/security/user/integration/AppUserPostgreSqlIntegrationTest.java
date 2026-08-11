package com.hx.creditcardflow.security.user.integration;

import com.hx.creditcardflow.security.user.entity.AppRole;
import com.hx.creditcardflow.security.user.entity.AppUser;
import com.hx.creditcardflow.security.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class AppUserPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void v10PersistsAndFindsApplicationUserWithEncodedSecurityState() {
        String passwordHash = passwordEncoder.encode("synthetic-integration-password");
        AppUser saved = appUserRepository.saveAndFlush(new AppUser(
                "integration-admin", passwordHash, AppRole.ADMIN, false
        ));

        AppUser reloaded = appUserRepository.findByUsername("integration-admin")
                .orElseThrow();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.app_users')", String.class
        )).isEqualTo("app_users");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '10' AND success", Integer.class
        )).isEqualTo(1);
        assertThat(saved.getId()).isNotNull();
        assertThat(reloaded.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(reloaded.getPasswordHash())
                .isNotEqualTo("synthetic-integration-password");
        assertThat(reloaded.getRole()).isEqualTo(AppRole.ADMIN);
        assertThat(reloaded.isEnabled()).isFalse();
    }
}
