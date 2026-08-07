package com.hx.creditcardflow.cardaccount.repository;

import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
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
class CardAccountRepositoryPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Test
    @Transactional
    void shouldSaveAndFindByAccountNumber() {
        CardAccount cardAccount = createCardAccount("ACC-320001");

        cardAccountRepository.saveAndFlush(cardAccount);

        assertThat(cardAccountRepository.findByAccountNumber("ACC-320001"))
                .isPresent()
                .get()
                .extracting(CardAccount::getAccountNumber)
                .isEqualTo("ACC-320001");
    }

    @Test
    @Transactional
    void shouldReturnEmptyWhenAccountNumberDoesNotExist() {
        assertThat(cardAccountRepository.findByAccountNumber("ACC-NOT-FOUND")).isEmpty();
    }

    @Test
    @Transactional
    void shouldReportWhetherAccountNumberExists() {
        cardAccountRepository.saveAndFlush(createCardAccount("ACC-320002"));

        assertThat(cardAccountRepository.existsByAccountNumber("ACC-320002")).isTrue();
        assertThat(cardAccountRepository.existsByAccountNumber("ACC-NOT-FOUND")).isFalse();
    }

    private CardAccount createCardAccount(String accountNumber) {
        return new CardAccount(
                accountNumber,
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
    }
}
