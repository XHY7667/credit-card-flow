package com.hx.creditcardflow.reversal.integration;

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
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import com.hx.creditcardflow.reversal.entity.AuthorizationReversal;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import com.hx.creditcardflow.reversal.repository.AuthorizationReversalRepository;
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
class AuthorizationReversalPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuthorizationReversalRepository reversalRepository;

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
        reversalRepository.deleteAll();
        authorizationRepository.deleteAll();
        cardRepository.deleteAll();
        cardAccountRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void shouldPersistAndReloadReversalWithAuthorizationRelationship() {
        Authorization authorization = persistAuthorization("510001", AuthorizationStatus.APPROVED);
        AuthorizationReversal reversal = reversalRepository.saveAndFlush(new AuthorizationReversal(
                "REV-510001",
                "IDEM-510001",
                authorization,
                new BigDecimal("125.75"),
                ReversalStatus.PENDING
        ));
        Long reversalId = reversal.getId();
        Long authorizationId = authorization.getId();

        entityManager.clear();

        AuthorizationReversal persisted = reversalRepository.findById(reversalId).orElseThrow();
        assertThat(persisted.getReversalReference()).isEqualTo("REV-510001");
        assertThat(persisted.getIdempotencyKey()).isEqualTo("IDEM-510001");
        assertThat(reversalRepository.findByIdempotencyKey("IDEM-510001"))
                .contains(persisted);
        assertThat(persisted.getAuthorization().getId()).isEqualTo(authorizationId);
        assertThat(persisted.getAuthorization().getAuthorizationReference()).isEqualTo("AUTH-510001");
        assertThat(persisted.getAmount()).isEqualByComparingTo("125.75");
        assertThat(persisted.getStatus()).isEqualTo(ReversalStatus.PENDING);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldEnforceUniqueReversalReference() {
        Authorization authorization = persistAuthorization("510002", AuthorizationStatus.APPROVED);
        reversalRepository.saveAndFlush(new AuthorizationReversal(
                "REV-DUPLICATE",
                "IDEM-510002-A",
                authorization,
                new BigDecimal("20.00"),
                ReversalStatus.COMPLETED
        ));

        AuthorizationReversal duplicate = new AuthorizationReversal(
                "REV-DUPLICATE",
                "IDEM-510002-B",
                authorization,
                new BigDecimal("20.00"),
                ReversalStatus.PENDING
        );

        assertThatThrownBy(() -> reversalRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldEnforceUniqueIdempotencyKey() {
        Authorization authorization = persistAuthorization("510003", AuthorizationStatus.APPROVED);
        reversalRepository.saveAndFlush(new AuthorizationReversal(
                "REV-510003-A",
                "IDEM-DUPLICATE",
                authorization,
                new BigDecimal("20.00"),
                ReversalStatus.COMPLETED
        ));

        AuthorizationReversal duplicate = new AuthorizationReversal(
                "REV-510003-B",
                "IDEM-DUPLICATE",
                authorization,
                new BigDecimal("20.00"),
                ReversalStatus.COMPLETED
        );

        assertThatThrownBy(() -> reversalRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldSupportAllAuthorizationStatusesAndSuccessfulV7Migration() {
        for (AuthorizationStatus status : AuthorizationStatus.values()) {
            persistAuthorization("5101" + status.ordinal(), status);
        }

        entityManager.flush();

        assertThat(authorizationRepository.findAll())
                .extracting(Authorization::getStatus)
                .containsExactlyInAnyOrder(
                        AuthorizationStatus.PENDING,
                        AuthorizationStatus.APPROVED,
                        AuthorizationStatus.DECLINED,
                        AuthorizationStatus.REVERSED
                );
        Integer appliedV7 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success",
                Integer.class
        );
        assertThat(appliedV7).isEqualTo(1);

        String nullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'authorization_reversals' "
                        + "AND column_name = 'idempotency_key'",
                String.class
        );
        assertThat(nullable).isEqualTo("NO");
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
                "Reversal Test Merchant " + suffix,
                "Reversal Merchant " + suffix,
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
