package com.hx.creditcardflow.merchant.integration;

import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
    void shouldApplyFlywayMigrationAndPersistMerchant() {
        String merchantsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.merchants')",
                String.class
        );

        Integer successfulVersionOneMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success",
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
        assertThat(successfulVersionOneMigrations).isEqualTo(1);
        assertThat(savedMerchant.getId()).isNotNull();
        assertThat(merchantRepository.findById(savedMerchant.getId()))
                .isPresent()
                .get()
                .extracting(Merchant::getMerchantCode)
                .isEqualTo("PG300001");
    }
}
