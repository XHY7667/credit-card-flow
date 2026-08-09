package com.hx.creditcardflow.authorization.integration;

import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import com.hx.creditcardflow.authorization.repository.AuthorizationRepository;
import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class AuthorizationPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuthorizationRepository authorizationRepository;

    @Test
    @Transactional
    void shouldPersistAndReloadAuthorizationWithRelationshipsAndValues() {
        TestRelationships relationships = persistRelationships("410001");
        Authorization authorization = authorizationRepository.saveAndFlush(new Authorization(
                "AUTH-410001",
                relationships.card(),
                relationships.merchant(),
                new BigDecimal("125.75"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.PAYPAL,
                AuthorizationStatus.PENDING
        ));
        Long authorizationId = authorization.getId();

        entityManager.clear();

        Authorization persisted = authorizationRepository.findById(authorizationId).orElseThrow();

        assertThat(persisted.getAuthorizationReference()).isEqualTo("AUTH-410001");
        assertThat(persisted.getCard().getCardReference()).isEqualTo("CARD-410001");
        assertThat(persisted.getMerchant().getMerchantCode()).isEqualTo("MER-410001");
        assertThat(persisted.getAmount()).isEqualByComparingTo("125.75");
        assertThat(persisted.getCurrencyCode()).isEqualTo("USD");
        assertThat(persisted.getAuthorizationType()).isEqualTo(AuthorizationType.PURCHASE);
        assertThat(persisted.getChannel()).isEqualTo(AuthorizationChannel.PAYPAL);
        assertThat(persisted.getStatus()).isEqualTo(AuthorizationStatus.PENDING);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void shouldEnforceUniqueAuthorizationReference() {
        TestRelationships relationships = persistRelationships("410002");
        authorizationRepository.saveAndFlush(new Authorization(
                "AUTH-DUPLICATE",
                relationships.card(),
                relationships.merchant(),
                new BigDecimal("20.00"),
                "USD",
                AuthorizationType.CASH_WITHDRAWAL,
                AuthorizationChannel.ATM,
                AuthorizationStatus.APPROVED
        ));

        Authorization duplicate = new Authorization(
                "AUTH-DUPLICATE",
                relationships.card(),
                relationships.merchant(),
                new BigDecimal("30.00"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.ALIPAY,
                AuthorizationStatus.DECLINED
        );

        assertThatThrownBy(() -> authorizationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TestRelationships persistRelationships(String suffix) {
        CardAccount cardAccount = new CardAccount(
                "ACC-" + suffix,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
        entityManager.persist(cardAccount);

        Card card = new Card(
                "CARD-" + suffix,
                "4242",
                12,
                2030,
                CardStatus.ACTIVE,
                cardAccount
        );
        entityManager.persist(card);

        Merchant merchant = new Merchant(
                "MER-" + suffix,
                "Authorization Test Merchant " + suffix,
                "Test Merchant " + suffix,
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );
        entityManager.persist(merchant);
        entityManager.flush();

        return new TestRelationships(card, merchant);
    }

    private record TestRelationships(Card card, Merchant merchant) {
    }
}
