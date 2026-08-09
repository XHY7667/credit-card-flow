package com.hx.creditcardflow.card.integration;

import com.hx.creditcardflow.card.dto.CardCreateRequest;
import com.hx.creditcardflow.card.dto.CardUpdateRequest;
import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.card.repository.CardRepository;
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
import java.time.YearMonth;
import java.time.ZoneOffset;

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
class CardApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardAccountRepository cardAccountRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        cardRepository.deleteAll();
        cardAccountRepository.deleteAll();
    }

    @Test
    void shouldIssueAndPersistCardForActiveCardAccount() throws Exception {
        CardAccount cardAccount = saveCardAccount("ACC-313001", CardAccountStatus.ACTIVE);
        CardCreateRequest request = createRequest(
                "CARD-313001", "0001", "ACC-313001", futureExpiration()
        );

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardReference").value("CARD-313001"))
                .andExpect(jsonPath("$.lastFour").value("0001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cardAccountNumber").value("ACC-313001"));

        Card persisted = cardRepository.findByCardReference("CARD-313001").orElseThrow();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(persisted.getCardAccount().getId()).isEqualTo(cardAccount.getId());
    }

    @Test
    void shouldCreateAndGetCard() throws Exception {
        saveCardAccount("ACC-313002", CardAccountStatus.ACTIVE);
        CardCreateRequest request = createRequest(
                "CARD-313002", "4242", "ACC-313002", futureExpiration()
        );
        createThroughApi(request);

        mockMvc.perform(get("/api/v1/cards/{cardReference}", "CARD-313002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardReference").value("CARD-313002"))
                .andExpect(jsonPath("$.lastFour").value("4242"))
                .andExpect(jsonPath("$.expirationMonth").value(request.expirationMonth()))
                .andExpect(jsonPath("$.expirationYear").value(request.expirationYear()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cardAccountNumber").value("ACC-313002"));
    }

    @Test
    void shouldUpdateCardStatusAndPersistChange() throws Exception {
        saveCardAccount("ACC-313003", CardAccountStatus.ACTIVE);
        createThroughApi(createRequest(
                "CARD-313003", "3133", "ACC-313003", futureExpiration()
        ));
        CardUpdateRequest request = new CardUpdateRequest(CardStatus.BLOCKED);

        mockMvc.perform(put("/api/v1/cards/{cardReference}", "CARD-313003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardReference").value("CARD-313003"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        assertThat(cardRepository.findByCardReference("CARD-313003"))
                .isPresent()
                .get()
                .extracting(Card::getStatus)
                .isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    void shouldRejectDuplicateCardReference() throws Exception {
        saveCardAccount("ACC-313004", CardAccountStatus.ACTIVE);
        CardCreateRequest request = createRequest(
                "CARD-313004", "4444", "ACC-313004", futureExpiration()
        );
        createThroughApi(request);

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Card reference already exists: CARD-313004"))
                .andExpect(jsonPath("$.path").value("/api/v1/cards"));

        assertThat(cardRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnNotFoundForMissingCard() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardReference}", "CARD-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card not found with card reference: CARD-NOT-FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/cards/CARD-NOT-FOUND"));
    }

    @Test
    void shouldRejectIssuanceForMissingCardAccount() throws Exception {
        CardCreateRequest request = createRequest(
                "CARD-313005", "5555", "ACC-NOT-FOUND", futureExpiration()
        );

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card account not found with account number: ACC-NOT-FOUND"));

        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void shouldRejectIssuanceForSuspendedCardAccount() throws Exception {
        saveCardAccount("ACC-313006", CardAccountStatus.SUSPENDED);
        CardCreateRequest request = createRequest(
                "CARD-313006", "6666", "ACC-313006", futureExpiration()
        );

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Card account is not eligible for card issuance: ACC-313006 with status SUSPENDED"));

        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void shouldRejectStructurallyValidPastExpiration() throws Exception {
        saveCardAccount("ACC-313007", CardAccountStatus.ACTIVE);
        YearMonth pastExpiration = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        CardCreateRequest request = createRequest(
                "CARD-313007", "7777", "ACC-313007", pastExpiration
        );

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Card expiration is before the current month: " + pastExpiration));

        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void shouldRejectInvalidCreateRequestWithoutPersistingCard() throws Exception {
        CardCreateRequest request = new CardCreateRequest(" ", "12A", 13, 1999, " ");

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.cardReference").exists())
                .andExpect(jsonPath("$.validationErrors.lastFour").exists())
                .andExpect(jsonPath("$.validationErrors.expirationMonth").exists())
                .andExpect(jsonPath("$.validationErrors.expirationYear").exists())
                .andExpect(jsonPath("$.validationErrors.cardAccountNumber").exists());

        assertThat(cardRepository.count()).isZero();
    }

    private void createThroughApi(CardCreateRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private CardAccount saveCardAccount(String accountNumber, CardAccountStatus status) {
        return cardAccountRepository.saveAndFlush(new CardAccount(
                accountNumber,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                "USD",
                status
        ));
    }

    private static CardCreateRequest createRequest(
            String cardReference,
            String lastFour,
            String cardAccountNumber,
            YearMonth expiration
    ) {
        return new CardCreateRequest(
                cardReference,
                lastFour,
                expiration.getMonthValue(),
                expiration.getYear(),
                cardAccountNumber
        );
    }

    private static YearMonth futureExpiration() {
        return YearMonth.now(ZoneOffset.UTC).plusYears(2);
    }
}
