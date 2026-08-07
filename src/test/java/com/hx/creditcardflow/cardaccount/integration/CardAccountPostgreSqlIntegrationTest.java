package com.hx.creditcardflow.cardaccount.integration;

import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class CardAccountPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void shouldPersistAndReadCardAccount() {
        CardAccount cardAccount = new CardAccount(
                "ACC-300001",
                new BigDecimal("5000.00"),
                new BigDecimal("1250.50"),
                new BigDecimal("3749.50"),
                "USD",
                CardAccountStatus.ACTIVE
        );

        entityManager.persist(cardAccount);
        entityManager.flush();

        Long cardAccountId = cardAccount.getId();
        Long initialVersion = cardAccount.getVersion();

        entityManager.clear();

        CardAccount persistedCardAccount = entityManager.find(CardAccount.class, cardAccountId);

        assertThat(cardAccountId).isNotNull();
        assertThat(initialVersion).isNotNull();
        assertThat(persistedCardAccount).isNotNull();
        assertThat(persistedCardAccount.getAccountNumber()).isEqualTo("ACC-300001");
        assertThat(persistedCardAccount.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(persistedCardAccount.getCurrentBalance()).isEqualByComparingTo("1250.50");
        assertThat(persistedCardAccount.getAvailableCredit()).isEqualByComparingTo("3749.50");
        assertThat(persistedCardAccount.getCurrencyCode()).isEqualTo("USD");
        assertThat(persistedCardAccount.getStatus()).isEqualTo(CardAccountStatus.ACTIVE);
        assertThat(persistedCardAccount.getVersion()).isEqualTo(initialVersion);
        assertThat(persistedCardAccount.getCreatedAt()).isNotNull();
        assertThat(persistedCardAccount.getUpdatedAt()).isNotNull();
    }
}
