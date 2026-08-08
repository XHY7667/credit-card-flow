package com.hx.creditcardflow.cardaccount.integration;

import com.hx.creditcardflow.cardaccount.dto.CardAccountCreateRequest;
import com.hx.creditcardflow.cardaccount.dto.CardAccountUpdateRequest;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.repository.CardAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Testcontainers
class CardAccountApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        cardAccountRepository.deleteAll();
    }

    @Test
    void shouldCreateAndPersistCardAccount() throws Exception {
        CardAccountCreateRequest request = createRequest("ACC-360001", "10000.00");

        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("ACC-360001"))
                .andExpect(jsonPath("$.creditLimit").value(10000.00))
                .andExpect(jsonPath("$.currentBalance").value(0))
                .andExpect(jsonPath("$.availableCredit").value(10000.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        CardAccount persisted = cardAccountRepository.findByAccountNumber("ACC-360001").orElseThrow();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(persisted.getAvailableCredit()).isEqualByComparingTo("10000.00");
        assertThat(persisted.getStatus()).isEqualTo(CardAccountStatus.ACTIVE);
    }

    @Test
    void shouldCreateAndGetCardAccount() throws Exception {
        CardAccountCreateRequest request = createRequest("ACC-360002", "7500.00");
        createThroughApi(request);

        mockMvc.perform(get("/api/v1/card-accounts/{accountNumber}", "ACC-360002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-360002"))
                .andExpect(jsonPath("$.creditLimit").value(7500.00))
                .andExpect(jsonPath("$.currentBalance").value(0))
                .andExpect(jsonPath("$.availableCredit").value(7500.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateCreditLimitAndStatusAndPersistChanges() throws Exception {
        createThroughApi(createRequest("ACC-360003", "10000.00"));
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("15000.00"), CardAccountStatus.SUSPENDED
        );

        mockMvc.perform(put("/api/v1/card-accounts/{accountNumber}", "ACC-360003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(15000.00))
                .andExpect(jsonPath("$.availableCredit").value(15000.00))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        CardAccount persisted = cardAccountRepository.findByAccountNumber("ACC-360003").orElseThrow();
        assertThat(persisted.getCreditLimit()).isEqualByComparingTo("15000.00");
        assertThat(persisted.getAvailableCredit()).isEqualByComparingTo("15000.00");
        assertThat(persisted.getStatus()).isEqualTo(CardAccountStatus.SUSPENDED);
    }

    @Test
    void shouldPreserveCommittedExposureThroughHttpServiceAndDatabase() throws Exception {
        cardAccountRepository.saveAndFlush(accountWithCommittedExposure("ACC-360004"));
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("15000.00"), CardAccountStatus.ACTIVE
        );

        mockMvc.perform(put("/api/v1/card-accounts/{accountNumber}", "ACC-360004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value(15000.00))
                .andExpect(jsonPath("$.currentBalance").value(2000.00))
                .andExpect(jsonPath("$.availableCredit").value(12000.00));

        CardAccount persisted = cardAccountRepository.findByAccountNumber("ACC-360004").orElseThrow();
        assertThat(persisted.getCreditLimit()).isEqualByComparingTo("15000.00");
        assertThat(persisted.getCurrentBalance()).isEqualByComparingTo("2000.00");
        assertThat(persisted.getAvailableCredit()).isEqualByComparingTo("12000.00");
    }

    @Test
    void shouldRejectDuplicateAccountCreation() throws Exception {
        CardAccountCreateRequest request = createRequest("ACC-360005", "10000.00");
        createThroughApi(request);

        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Card account number already exists: ACC-360005"))
                .andExpect(jsonPath("$.path").value("/api/v1/card-accounts"));

        assertThat(cardAccountRepository.findAll())
                .filteredOn(account -> account.getAccountNumber().equals("ACC-360005"))
                .hasSize(1);
    }

    @Test
    void shouldReturnNotFoundForNonexistentAccount() throws Exception {
        mockMvc.perform(get("/api/v1/card-accounts/{accountNumber}", "ACC-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card account not found with account number: ACC-NOT-FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/card-accounts/ACC-NOT-FOUND"));
    }

    @Test
    void shouldRejectCreditLimitBelowCommittedExposureWithoutChangingAccount() throws Exception {
        cardAccountRepository.saveAndFlush(accountWithCommittedExposure("ACC-360006"));
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("2000.00"), CardAccountStatus.SUSPENDED
        );

        mockMvc.perform(put("/api/v1/card-accounts/{accountNumber}", "ACC-360006")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Card account credit limit 2000.00 cannot be below committed exposure 3000.00"));

        CardAccount persisted = cardAccountRepository.findByAccountNumber("ACC-360006").orElseThrow();
        assertThat(persisted.getCreditLimit()).isEqualByComparingTo("10000.00");
        assertThat(persisted.getAvailableCredit()).isEqualByComparingTo("7000.00");
        assertThat(persisted.getStatus()).isEqualTo(CardAccountStatus.ACTIVE);
    }

    @Test
    void shouldRejectInvalidCreateRequestWithoutPersistingData() throws Exception {
        CardAccountCreateRequest request = new CardAccountCreateRequest(" ", BigDecimal.ZERO, "usd");

        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.accountNumber").exists())
                .andExpect(jsonPath("$.validationErrors.creditLimit").exists())
                .andExpect(jsonPath("$.validationErrors.currencyCode").exists());

        assertThat(cardAccountRepository.count()).isZero();
    }

    private void createThroughApi(CardAccountCreateRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private static CardAccountCreateRequest createRequest(String accountNumber, String creditLimit) {
        return new CardAccountCreateRequest(accountNumber, new BigDecimal(creditLimit), "USD");
    }

    private static CardAccount accountWithCommittedExposure(String accountNumber) {
        return new CardAccount(
                accountNumber,
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("7000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
    }
}
