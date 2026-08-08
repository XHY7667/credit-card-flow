package com.hx.creditcardflow.card.integration;

import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
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
class CardPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void shouldPersistAndReadCardWithCardAccountRelationship() {
        CardAccount cardAccount = new CardAccount(
                "ACC-380001",
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
        entityManager.persist(cardAccount);
        entityManager.flush();

        Card card = new Card(
                "CARD-380001",
                "4242",
                12,
                2030,
                CardStatus.ACTIVE,
                cardAccount
        );
        entityManager.persist(card);
        entityManager.flush();

        Long cardId = card.getId();
        Long cardAccountId = cardAccount.getId();

        entityManager.clear();

        Card persistedCard = entityManager.find(Card.class, cardId);

        assertThat(cardId).isNotNull();
        assertThat(persistedCard).isNotNull();
        assertThat(persistedCard.getCardReference()).isEqualTo("CARD-380001");
        assertThat(persistedCard.getLastFour()).isEqualTo("4242");
        assertThat(persistedCard.getExpirationMonth()).isEqualTo(12);
        assertThat(persistedCard.getExpirationYear()).isEqualTo(2030);
        assertThat(persistedCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(persistedCard.getCreatedAt()).isNotNull();
        assertThat(persistedCard.getUpdatedAt()).isNotNull();
        assertThat(persistedCard.getCardAccount().getId()).isEqualTo(cardAccountId);
        assertThat(persistedCard.getCardAccount().getAccountNumber()).isEqualTo("ACC-380001");
    }
}
