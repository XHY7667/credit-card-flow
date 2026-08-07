package com.hx.creditcardflow.merchant.integration;

import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class MerchantPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldApplyFlywayMigrationsEnforceStatusConstraintAndPersistMerchant() {
        String merchantsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.merchants')",
                String.class
        );

        List<String> successfulMigrationVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE version IN ('1', '2') AND success ORDER BY installed_rank",
                String.class
        );

        Integer statusCheckConstraints = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint constraint_definition "
                        + "JOIN pg_class table_definition "
                        + "ON table_definition.oid = constraint_definition.conrelid "
                        + "WHERE table_definition.relname = 'merchants' "
                        + "AND constraint_definition.conname = 'ck_merchants_status' "
                        + "AND constraint_definition.contype = 'c'",
                Integer.class
        );

        Merchant merchant = new Merchant(
                "PG300001",
                "PostgreSQL Integration Merchant LLC",
                "PostgreSQL Merchant",
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );

        Merchant savedMerchant = merchantRepository.saveAndFlush(merchant);

        assertThat(merchantsTable).isEqualTo("merchants");
        assertThat(successfulMigrationVersions).containsExactly("1", "2");
        assertThat(statusCheckConstraints).isEqualTo(1);
        assertThat(savedMerchant.getId()).isNotNull();
        assertThat(merchantRepository.findById(savedMerchant.getId()))
                .isPresent()
                .get()
                .extracting(Merchant::getMerchantCode)
                .isEqualTo("PG300001");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO merchants "
                        + "(merchant_code, legal_name, display_name, merchant_category_code, "
                        + "country_code, status, created_at, updated_at) "
                        + "VALUES ('PGINVALID1', 'Invalid Status Merchant LLC', "
                        + "'Invalid Status Merchant', '5411', 'US', 'INVALID', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
