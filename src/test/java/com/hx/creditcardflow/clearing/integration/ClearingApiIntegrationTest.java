package com.hx.creditcardflow.clearing.integration;

import com.hx.creditcardflow.authorization.dto.AuthorizationCreateRequest;
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
import com.hx.creditcardflow.clearing.dto.ClearingCreateRequest;
import com.hx.creditcardflow.clearing.entity.Clearing;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.repository.ClearingRepository;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
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
@AutoConfigureMockMvc
@Testcontainers
class ClearingApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired ClearingRepository clearingRepository;
    @Autowired AuthorizationReversalRepository reversalRepository;
    @Autowired AuthorizationRepository authorizationRepository;
    @Autowired CardRepository cardRepository;
    @Autowired CardAccountRepository cardAccountRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        clearingRepository.deleteAll();
        reversalRepository.deleteAll();
        authorizationRepository.deleteAll();
        cardRepository.deleteAll();
        cardAccountRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void authorizationClearingGetDuplicateAndAlreadyClearedPreserveSingleFinancialConversion()
            throws Exception {
        Fixture fixture = createApprovedAuthorization("650001", "200.00", "USD");
        assertFinancialState(fixture, "0.00", "800.00", "200.00", "200.00");
        ClearingCreateRequest request = clearingRequest("CLR-650001", fixture, "200.00", "USD");

        postClearing(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clearingReference").value("CLR-650001"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-650001"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.status").value("POSTED"));

        Clearing persisted = clearingRepository.findByClearingReference("CLR-650001").orElseThrow();
        assertThat(clearingRepository.count()).isOne();
        assertThat(persisted.getStatus()).isEqualTo(ClearingStatus.POSTED);
        assertThat(clearingAuthorizationId("CLR-650001"))
                .isEqualTo(fixture.authorization().getId());
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.CLEARED);
        assertFinancialState(fixture, "200.00", "800.00", "200.00", "0.00");

        mockMvc.perform(get("/api/v1/clearings/{clearingReference}", "CLR-650001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearingReference").value("CLR-650001"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-650001"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.status").value("POSTED"));

        postClearing(request).andExpect(status().isConflict());
        postClearing(clearingRequest("CLR-650001-NEW", fixture, "200.00", "USD"))
                .andExpect(status().isConflict());

        assertThat(clearingRepository.count()).isOne();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.CLEARED);
        assertFinancialState(fixture, "200.00", "800.00", "200.00", "0.00");
    }

    @Test
    void amountMismatchLeavesAuthorizationReservationAndNoClearing() throws Exception {
        Fixture fixture = createApprovedAuthorization("650002", "200.00", "USD");

        postClearing(clearingRequest("CLR-650002", fixture, "199.99", "USD"))
                .andExpect(status().isConflict());

        assertRejectedState(fixture, "800.00");
    }

    @Test
    void currencyMismatchLeavesAuthorizationReservationAndNoClearing() throws Exception {
        Fixture fixture = createApprovedAuthorization("650003", "200.00", "USD");

        postClearing(clearingRequest("CLR-650003", fixture, "200.00", "EUR"))
                .andExpect(status().isConflict());

        assertRejectedState(fixture, "800.00");
    }

    @Test
    void numericallyEqualAmountWithDifferentScalePostsSuccessfully() throws Exception {
        Fixture fixture = createApprovedAuthorization("650004", "200.00", "USD");

        postClearing(clearingRequest("CLR-650004", fixture, "200.0", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"));

        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.CLEARED);
        assertFinancialState(fixture, "200.00", "800.00", "200.00", "0.00");
    }

    @Test
    void missingAuthorizationReturnsNotFoundWithoutUnrelatedFinancialMutation() throws Exception {
        Fixture unrelated = createApprovedAuthorization("650005", "50.00", "USD");
        ClearingCreateRequest request = new ClearingCreateRequest(
                "CLR-650005", "AUTH-NOT-FOUND", new BigDecimal("50.00"), "USD"
        );

        postClearing(request).andExpect(status().isNotFound());

        assertThat(clearingRepository.count()).isZero();
        assertThat(reloadAuthorization(unrelated).getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
        assertFinancialState(unrelated, "0.00", "950.00", "50.00", "50.00");
    }

    @Test
    void changedCardAccountAndMerchantStatusesDoNotBlockMatchedClearing() throws Exception {
        Fixture fixture = createApprovedAuthorization("650006", "200.00", "USD");
        Card card = cardRepository.findById(fixture.card().getId()).orElseThrow();
        card.changeStatus(CardStatus.BLOCKED);
        cardRepository.save(card);
        CardAccount account = reloadAccount(fixture);
        account.update(account.getCreditLimit(), account.getAvailableCredit(), CardAccountStatus.SUSPENDED);
        cardAccountRepository.save(account);
        Merchant merchant = merchantRepository.findById(fixture.merchant().getId()).orElseThrow();
        merchant.setStatus(MerchantStatus.SUSPENDED);
        merchantRepository.save(merchant);

        postClearing(clearingRequest("CLR-650006", fixture, "200.00", "USD"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"));

        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.CLEARED);
        assertFinancialState(fixture, "200.00", "800.00", "200.00", "0.00");
    }

    @Test
    void beanValidationFailureReturnsBadRequestWithoutFinancialMutation() throws Exception {
        Fixture fixture = createApprovedAuthorization("650007", "200.00", "USD");
        ClearingCreateRequest invalid = new ClearingCreateRequest(
                " ", fixture.authorization().getAuthorizationReference(), BigDecimal.ZERO, "usd"
        );

        postClearing(invalid).andExpect(status().isBadRequest());

        assertRejectedState(fixture, "800.00");
    }

    @Test
    void getMissingClearingReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/clearings/{clearingReference}", "CLR-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    private Fixture createApprovedAuthorization(String suffix, String amount, String currency)
            throws Exception {
        CardAccount account = cardAccountRepository.save(new CardAccount(
                "ACC-" + suffix, new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("1000.00"), currency, CardAccountStatus.ACTIVE
        ));
        Card card = cardRepository.save(new Card(
                "CARD-" + suffix, "4242", 12, 2030, CardStatus.ACTIVE, account
        ));
        Merchant merchant = merchantRepository.save(new Merchant(
                "MER-" + suffix, "Clearing API Merchant " + suffix, "Clearing Merchant",
                "5411", "US", MerchantStatus.ACTIVE
        ));
        AuthorizationCreateRequest request = new AuthorizationCreateRequest(
                "AUTH-" + suffix, card.getCardReference(), merchant.getMerchantCode(),
                new BigDecimal(amount), currency, AuthorizationType.PURCHASE, AuthorizationChannel.POS
        );

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Authorization authorization = authorizationRepository
                .findByAuthorizationReference("AUTH-" + suffix).orElseThrow();
        return new Fixture(account, card, merchant, authorization);
    }

    private org.springframework.test.web.servlet.ResultActions postClearing(
            ClearingCreateRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/clearings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    private static ClearingCreateRequest clearingRequest(
            String clearingReference, Fixture fixture, String amount, String currency) {
        return new ClearingCreateRequest(
                clearingReference,
                fixture.authorization().getAuthorizationReference(),
                new BigDecimal(amount),
                currency
        );
    }

    private void assertRejectedState(Fixture fixture, String availableCredit) {
        assertThat(clearingRepository.count()).isZero();
        assertThat(reloadAuthorization(fixture).getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
        assertFinancialState(fixture, "0.00", availableCredit,
                new BigDecimal("1000.00").subtract(new BigDecimal(availableCredit)).toPlainString(),
                new BigDecimal("1000.00").subtract(new BigDecimal(availableCredit)).toPlainString());
    }

    private void assertFinancialState(
            Fixture fixture,
            String currentBalance,
            String availableCredit,
            String totalExposure,
            String pendingExposure
    ) {
        CardAccount account = reloadAccount(fixture);
        assertThat(account.getCreditLimit()).isEqualByComparingTo("1000.00");
        assertThat(account.getCurrentBalance()).isEqualByComparingTo(currentBalance);
        assertThat(account.getAvailableCredit()).isEqualByComparingTo(availableCredit);
        BigDecimal total = account.getCreditLimit().subtract(account.getAvailableCredit());
        assertThat(total).isEqualByComparingTo(totalExposure);
        assertThat(total.subtract(account.getCurrentBalance())).isEqualByComparingTo(pendingExposure);
    }

    private CardAccount reloadAccount(Fixture fixture) {
        return cardAccountRepository.findById(fixture.account().getId()).orElseThrow();
    }

    private Authorization reloadAuthorization(Fixture fixture) {
        return authorizationRepository.findById(fixture.authorization().getId()).orElseThrow();
    }

    private Long clearingAuthorizationId(String clearingReference) {
        return jdbcTemplate.queryForObject(
                "SELECT authorization_id FROM clearings WHERE clearing_reference = ?",
                Long.class, clearingReference
        );
    }

    private record Fixture(
            CardAccount account,
            Card card,
            Merchant merchant,
            Authorization authorization
    ) {
    }
}
