package com.hx.creditcardflow.cardaccount.integration;

import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.repository.CardAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
class CardAccountOptimisticLockIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        cardAccountRepository.deleteAll();
    }

    @Test
    void staleCardAccountUpdateFailsAndPreservesWinningState() {
        CardAccount saved = cardAccountRepository.saveAndFlush(new CardAccount(
                "ACC-450012",
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("7000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        ));
        Long initialVersion = saved.getVersion();

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(outerStatus -> {
            CardAccount stale = entityManager.find(CardAccount.class, saved.getId());

            independent.executeWithoutResult(innerStatus -> {
                CardAccount winner = entityManager.find(CardAccount.class, saved.getId());
                winner.reserveCredit(new BigDecimal("100.00"));
                entityManager.flush();
            });

            stale.reserveCredit(new BigDecimal("50.00"));
            entityManager.flush();
        })).isInstanceOfAny(
                ObjectOptimisticLockingFailureException.class,
                OptimisticLockException.class
        );

        CardAccount persisted = cardAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(persisted.getAvailableCredit()).isEqualByComparingTo("6900.00");
        assertThat(persisted.getVersion()).isEqualTo(initialVersion + 1);
    }
}
