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
import com.hx.creditcardflow.reversal.dto.ReversalCreateRequest;
import com.hx.creditcardflow.reversal.entity.AuthorizationReversal;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import com.hx.creditcardflow.reversal.repository.AuthorizationReversalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class ReversalApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired AuthorizationReversalRepository reversalRepository;
    @Autowired AuthorizationRepository authorizationRepository;
    @Autowired CardRepository cardRepository;
    @Autowired CardAccountRepository cardAccountRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        reversalRepository.deleteAll();
        authorizationRepository.deleteAll();
        cardRepository.deleteAll();
        cardAccountRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void successfulPostGetAndReplayPersistOnceAndReleaseCreditOnce() throws Exception {
        Fixture fixture = approvedFixture("560001", "125.75");
        ReversalCreateRequest request = request("REV-560001", fixture, "125.75");

        postReversal("REV-KEY-001", request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reversalReference").value("REV-560001"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-560001"))
                .andExpect(jsonPath("$.amount").value(125.75));

        AuthorizationReversal persisted = reversalRepository
                .findByReversalReference("REV-560001").orElseThrow();
        assertThat(reversalRepository.count()).isOne();
        assertThat(persisted.getIdempotencyKey()).isEqualTo("REV-KEY-001");
        assertThat(persisted.getStatus()).isEqualTo(ReversalStatus.COMPLETED);
        assertThat(reversalAuthorizationId("REV-560001")).isEqualTo(fixture.authorization().getId());
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.REVERSED);
        assertAccountCredit(fixture, "7000.00");

        mockMvc.perform(get("/api/v1/reversals/{reversalReference}", "REV-560001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalReference").value("REV-560001"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-560001"))
                .andExpect(jsonPath("$.amount").value(125.75))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        postReversal("REV-KEY-001", request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalReference").value("REV-560001"));
        assertThat(reversalRepository.count()).isOne();
        assertThat(reversalRepository.findByIdempotencyKey("REV-KEY-001")).isPresent();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.REVERSED);
        assertAccountCredit(fixture, "7000.00");
    }

    @Test
    void conflictingReplayDuplicateReferenceAndAlreadyReversedDoNotMutateAgain() throws Exception {
        Fixture fixture = approvedFixture("560002", "125.75");
        ReversalCreateRequest original = request("REV-560002", fixture, "125.75");
        postReversal("REV-KEY-002", original).andExpect(status().isCreated());

        postReversal("REV-KEY-002", request("REV-DIFFERENT", fixture, "125.75"))
                .andExpect(status().isConflict());
        postReversal("REV-KEY-DIFFERENT", original)
                .andExpect(status().isConflict());
        postReversal("REV-KEY-NEW", request("REV-NEW", fixture, "125.75"))
                .andExpect(status().isConflict());

        assertThat(reversalRepository.count()).isOne();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.REVERSED);
        assertAccountCredit(fixture, "7000.00");
    }

    @Test
    void amountMismatchLeavesReservationAndSameKeyCanThenSucceed() throws Exception {
        Fixture fixture = approvedFixture("560003", "125.75");

        postReversal("REV-KEY-003", request("REV-560003", fixture, "100.00"))
                .andExpect(status().isConflict());
        assertThat(reversalRepository.count()).isZero();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
        assertAccountCredit(fixture, "6874.25");

        postReversal("REV-KEY-003", request("REV-560003", fixture, "125.75"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(reversalRepository.count()).isOne();
        assertThat(reversalRepository.findByIdempotencyKey("REV-KEY-003")).isPresent();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.REVERSED);
        assertAccountCredit(fixture, "7000.00");
    }

    @Test
    void missingAuthorizationReturnsNotFoundWithoutPersistenceOrCreditMutation() throws Exception {
        Fixture fixture = approvedFixture("560004", "50.00");
        ReversalCreateRequest request = new ReversalCreateRequest(
                "REV-560004", "AUTH-NOT-FOUND", new BigDecimal("50.00"));

        postReversal("REV-KEY-004", request).andExpect(status().isNotFound());
        assertThat(reversalRepository.count()).isZero();
        assertAccountCredit(fixture, "6950.00");
    }

    @Test
    void missingIdempotencyHeaderReturnsBadRequestWithoutMutation() throws Exception {
        Fixture fixture = approvedFixture("560005", "50.00");
        mockMvc.perform(post("/api/v1/reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request("REV-560005", fixture, "50.00"))))
                .andExpect(status().isBadRequest());
        assertThat(reversalRepository.count()).isZero();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
        assertAccountCredit(fixture, "6950.00");
    }

    @Test
    void beanValidationFailureReturnsBadRequestWithoutMutation() throws Exception {
        Fixture fixture = approvedFixture("560006", "50.00");
        ReversalCreateRequest invalid = new ReversalCreateRequest(" ", "AUTH-560006", BigDecimal.ZERO);
        postReversal("REV-KEY-006", invalid).andExpect(status().isBadRequest());
        assertThat(reversalRepository.count()).isZero();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
        assertAccountCredit(fixture, "6950.00");
    }

    private Fixture approvedFixture(String suffix, String amount) {
        BigDecimal reservedAmount = new BigDecimal(amount);
        CardAccount account = cardAccountRepository.save(new CardAccount(
                "ACC-" + suffix, new BigDecimal("10000.00"), new BigDecimal("2000.00"),
                new BigDecimal("7000.00").subtract(reservedAmount), "USD", CardAccountStatus.ACTIVE));
        Card card = cardRepository.save(new Card(
                "CARD-" + suffix, "4242", 12, 2030, CardStatus.ACTIVE, account));
        Merchant merchant = merchantRepository.save(new Merchant(
                "MER-" + suffix, "Reversal API Merchant " + suffix, "Reversal Merchant",
                "5411", "US", MerchantStatus.ACTIVE));
        Authorization authorization = authorizationRepository.save(new Authorization(
                "AUTH-" + suffix, card, merchant, reservedAmount, "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS, AuthorizationStatus.APPROVED));
        return new Fixture(account, authorization);
    }

    private org.springframework.test.web.servlet.ResultActions postReversal(
            String key, ReversalCreateRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/reversals")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    private static ReversalCreateRequest request(String reference, Fixture fixture, String amount) {
        return new ReversalCreateRequest(reference, fixture.authorization().getAuthorizationReference(),
                new BigDecimal(amount));
    }

    private Authorization reloadAuthorization(Fixture fixture) {
        return authorizationRepository.findById(fixture.authorization().getId()).orElseThrow();
    }

    private void assertAccountCredit(Fixture fixture, String expected) {
        CardAccount account = cardAccountRepository.findById(fixture.account().getId()).orElseThrow();
        assertThat(account.getAvailableCredit()).isEqualByComparingTo(expected);
        assertThat(account.getAvailableCredit()).isLessThanOrEqualTo(account.getCreditLimit());
        assertThat(account.getAvailableCredit()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(account.getCreditLimit().subtract(account.getAvailableCredit()))
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    private Long reversalAuthorizationId(String reversalReference) {
        return jdbcTemplate.queryForObject(
                "SELECT authorization_id FROM authorization_reversals WHERE reversal_reference = ?",
                Long.class, reversalReference);
    }

    private record Fixture(CardAccount account, Authorization authorization) {
    }
}
