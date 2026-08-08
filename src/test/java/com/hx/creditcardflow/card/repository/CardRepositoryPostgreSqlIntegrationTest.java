package com.hx.creditcardflow.card.repository;

import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.repository.CardAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class CardRepositoryPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Test
    @Transactional
    void shouldSaveAndFindByCardReference() {
        CardAccount cardAccount = cardAccountRepository.saveAndFlush(
                createCardAccount("ACC-390001")
        );
        cardRepository.saveAndFlush(createCard("CARD-390001", cardAccount));

        assertThat(cardRepository.findByCardReference("CARD-390001"))
                .isPresent()
                .get()
                .satisfies(card -> {
                    assertThat(card.getCardReference()).isEqualTo("CARD-390001");
                    assertThat(card.getLastFour()).isEqualTo("4242");
                    assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
                    assertThat(card.getCardAccount().getAccountNumber()).isEqualTo("ACC-390001");
                });
    }

    @Test
    @Transactional
    void shouldReturnEmptyWhenCardReferenceDoesNotExist() {
        assertThat(cardRepository.findByCardReference("CARD-NOT-FOUND")).isEmpty();
    }

    @Test
    @Transactional
    void shouldReportWhetherCardReferenceExists() {
        CardAccount cardAccount = cardAccountRepository.saveAndFlush(
                createCardAccount("ACC-390002")
        );
        cardRepository.saveAndFlush(createCard("CARD-390002", cardAccount));

        assertThat(cardRepository.existsByCardReference("CARD-390002")).isTrue();
        assertThat(cardRepository.existsByCardReference("CARD-NOT-FOUND")).isFalse();
    }

    private static CardAccount createCardAccount(String accountNumber) {
        return new CardAccount(
                accountNumber,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("5000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
    }

    private static Card createCard(String cardReference, CardAccount cardAccount) {
        return new Card(
                cardReference,
                "4242",
                12,
                2030,
                CardStatus.ACTIVE,
                cardAccount
        );
    }
}
