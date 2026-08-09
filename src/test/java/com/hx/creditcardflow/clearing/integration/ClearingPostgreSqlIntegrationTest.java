package com.hx.creditcardflow.clearing.integration;

import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import com.hx.creditcardflow.authorization.repository.AuthorizationRepository;
import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.card.repository.CardRepository;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.repository.CardAccountRepository;
import com.hx.creditcardflow.clearing.entity.Clearing;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.repository.ClearingRepository;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
@Transactional
class ClearingPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ClearingRepository clearingRepository;

    @Autowired
    private AuthorizationRepository authorizationRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        clearingRepository.deleteAll();
        authorizationRepository.deleteAll();
        cardRepository.deleteAll();
        cardAccountRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void shouldPersistAndReloadClearingWithAuthorizationAndValues() {
        Authorization authorization = persistAuthorization("610001", AuthorizationStatus.APPROVED);
        Clearing clearing = clearingRepository.saveAndFlush(new Clearing(
                "CLR-610001",
                authorization,
                new BigDecimal("125.75"),
                "USD",
                ClearingStatus.PENDING
        ));
        Long clearingId = clearing.getId();
        Long authorizationId = authorization.getId();

        entityManager.clear();

        Clearing persisted = clearingRepository.findById(clearingId).orElseThrow();
        assertThat(clearingRepository.findByClearingReference("CLR-610001")).contains(persisted);
        assertThat(persisted.getClearingReference()).isEqualTo("CLR-610001");
        assertThat(persisted.getAuthorization().getId()).isEqualTo(authorizationId);
        assertThat(persisted.getAuthorization().getAuthorizationReference()).isEqualTo("AUTH-610001");
        assertThat(persisted.getAmount()).isEqualByComparingTo("125.75");
        assertThat(persisted.getCurrencyCode()).isEqualTo("USD");
        assertThat(persisted.getStatus()).isEqualTo(ClearingStatus.PENDING);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCreatedAt());

        Integer numericScale = jdbcTemplate.queryForObject(
                "SELECT numeric_scale FROM information_schema.columns "
                        + "WHERE table_name = 'clearings' AND column_name = 'amount'",
                Integer.class
        );
        Integer numericPrecision = jdbcTemplate.queryForObject(
                "SELECT numeric_precision FROM information_schema.columns "
                        + "WHERE table_name = 'clearings' AND column_name = 'amount'",
                Integer.class
        );
        assertThat(numericPrecision).isEqualTo(19);
        assertThat(numericScale).isEqualTo(2);
    }

    @Test
    void shouldEnforceUniqueClearingReference() {
        Authorization authorization = persistAuthorization("610002", AuthorizationStatus.APPROVED);
        clearingRepository.saveAndFlush(new Clearing(
                "CLR-DUPLICATE", authorization, new BigDecimal("20.00"), "USD", ClearingStatus.PENDING
        ));

        Clearing duplicate = new Clearing(
                "CLR-DUPLICATE", authorization, new BigDecimal("30.00"), "USD", ClearingStatus.PENDING
        );

        assertThatThrownBy(() -> clearingRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldSupportAllAuthorizationStatusesAndSuccessfulV8Migration() {
        for (AuthorizationStatus status : AuthorizationStatus.values()) {
            persistAuthorization("6101" + status.ordinal(), status);
        }

        entityManager.flush();

        assertThat(authorizationRepository.findAll())
                .extracting(Authorization::getStatus)
                .containsExactlyInAnyOrder(
                        AuthorizationStatus.PENDING,
                        AuthorizationStatus.APPROVED,
                        AuthorizationStatus.DECLINED,
                        AuthorizationStatus.REVERSED,
                        AuthorizationStatus.CLEARED
                );
        Integer appliedV8 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success",
                Integer.class
        );
        assertThat(appliedV8).isEqualTo(1);
    }

    private Authorization persistAuthorization(String suffix, AuthorizationStatus status) {
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
                "Clearing Test Merchant " + suffix,
                "Clearing Merchant " + suffix,
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );
        entityManager.persist(merchant);

        Authorization authorization = new Authorization(
                "AUTH-" + suffix,
                card,
                merchant,
                new BigDecimal("125.75"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.POS,
                status
        );
        entityManager.persist(authorization);
        entityManager.flush();
        return authorization;
    }
}
