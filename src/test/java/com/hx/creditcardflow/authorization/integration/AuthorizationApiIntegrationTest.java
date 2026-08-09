package com.hx.creditcardflow.authorization.integration;

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
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
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
import java.time.YearMonth;
import java.time.ZoneOffset;

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
class AuthorizationApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        authorizationRepository.deleteAll();
        cardRepository.deleteAll();
        cardAccountRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    void shouldApprovePersistRelationshipsAndReserveAvailableCredit() throws Exception {
        Fixture fixture = saveFixture("450001", CardStatus.ACTIVE, futureExpiration(),
                CardAccountStatus.ACTIVE, MerchantStatus.ACTIVE, "7000.00");
        Long initialVersion = fixture.cardAccount().getVersion();
        AuthorizationCreateRequest request = request("AUTH-450001", fixture, "125.75");

        postAuthorization(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-450001"))
                .andExpect(jsonPath("$.cardReference").value("CARD-450001"))
                .andExpect(jsonPath("$.merchantCode").value("MER-450001"))
                .andExpect(jsonPath("$.amount").value(125.75))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.authorizationType").value("PURCHASE"))
                .andExpect(jsonPath("$.channel").value("POS"));

        Authorization persisted = authorizationRepository
                .findByAuthorizationReference("AUTH-450001").orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
        assertThat(persisted.getAmount()).isEqualByComparingTo("125.75");
        assertThat(relatedId("card_id", "AUTH-450001")).isEqualTo(fixture.card().getId());
        assertThat(relatedId("merchant_id", "AUTH-450001")).isEqualTo(fixture.merchant().getId());

        CardAccount updated = reloadAccount(fixture);
        assertThat(updated.getAvailableCredit()).isEqualByComparingTo("6874.25");
        assertThat(updated.getCreditLimit()).isEqualByComparingTo("10000.00");
        assertThat(updated.getCurrentBalance()).isEqualByComparingTo("2000.00");
        assertThat(committedExposure(updated)).isEqualByComparingTo("3125.75");
        assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void shouldCreateThenGetPersistedAuthorization() throws Exception {
        Fixture fixture = saveActiveFixture("450002", "7000.00");
        AuthorizationCreateRequest request = request("AUTH-450002", fixture, "50.00");
        postAuthorization(request).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/authorizations/{authorizationReference}", "AUTH-450002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-450002"))
                .andExpect(jsonPath("$.cardReference").value("CARD-450002"))
                .andExpect(jsonPath("$.merchantCode").value("MER-450002"))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void shouldPersistInsufficientCreditDeclineWithoutChangingExposure() throws Exception {
        Fixture fixture = saveActiveFixture("450003", "100.00");
        assertDeclinedWithoutCreditMutation(fixture, request("AUTH-450003", fixture, "125.75"));
    }

    @Test
    void shouldPersistNonActiveCardDeclineWithoutChangingCredit() throws Exception {
        Fixture fixture = saveFixture("450004", CardStatus.BLOCKED, futureExpiration(),
                CardAccountStatus.ACTIVE, MerchantStatus.ACTIVE, "7000.00");
        assertDeclinedWithoutCreditMutation(fixture, request("AUTH-450004", fixture, "125.75"));
    }

    @Test
    void shouldPersistExpiredCardDeclineWithoutChangingCredit() throws Exception {
        Fixture fixture = saveFixture("450005", CardStatus.ACTIVE,
                YearMonth.now(ZoneOffset.UTC).minusMonths(1), CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE, "7000.00");
        assertDeclinedWithoutCreditMutation(fixture, request("AUTH-450005", fixture, "125.75"));
    }

    @Test
    void shouldPersistNonActiveCardAccountDeclineWithoutChangingCredit() throws Exception {
        Fixture fixture = saveFixture("450006", CardStatus.ACTIVE, futureExpiration(),
                CardAccountStatus.SUSPENDED, MerchantStatus.ACTIVE, "7000.00");
        assertDeclinedWithoutCreditMutation(fixture, request("AUTH-450006", fixture, "125.75"));
    }

    @Test
    void shouldPersistNonActiveMerchantDeclineWithoutChangingCredit() throws Exception {
        Fixture fixture = saveFixture("450007", CardStatus.ACTIVE, futureExpiration(),
                CardAccountStatus.ACTIVE, MerchantStatus.SUSPENDED, "7000.00");
        assertDeclinedWithoutCreditMutation(fixture, request("AUTH-450007", fixture, "125.75"));
    }

    @Test
    void shouldReturnNotFoundAndNotPersistWhenCardIsMissing() throws Exception {
        Merchant merchant = saveMerchant("450008", MerchantStatus.ACTIVE);
        AuthorizationCreateRequest request = new AuthorizationCreateRequest(
                "AUTH-450008", "CARD-NOT-FOUND", merchant.getMerchantCode(),
                new BigDecimal("25.00"), "USD", AuthorizationType.PURCHASE, AuthorizationChannel.POS
        );

        postAuthorization(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertThat(authorizationRepository.count()).isZero();
    }

    @Test
    void shouldReturnNotFoundAndNotPersistWhenMerchantIsMissing() throws Exception {
        CardAccount account = saveCardAccount("450009", CardAccountStatus.ACTIVE, "7000.00");
        Card card = saveCard("450009", CardStatus.ACTIVE, futureExpiration(), account);
        AuthorizationCreateRequest request = new AuthorizationCreateRequest(
                "AUTH-450009", card.getCardReference(), "MER-NOT-FOUND",
                new BigDecimal("25.00"), "USD", AuthorizationType.PURCHASE, AuthorizationChannel.POS
        );

        postAuthorization(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertThat(authorizationRepository.count()).isZero();
        assertThat(reloadAccount(account).getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    @Test
    void duplicateReferenceDoesNotPersistTwiceOrReserveAdditionalCredit() throws Exception {
        Fixture fixture = saveActiveFixture("450010", "7000.00");
        AuthorizationCreateRequest request = request("AUTH-450010", fixture, "125.75");
        postAuthorization(request).andExpect(status().isCreated());
        BigDecimal creditAfterFirstRequest = reloadAccount(fixture).getAvailableCredit();

        postAuthorization(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));

        assertThat(authorizationRepository.findAll())
                .filteredOn(authorization -> authorization.getAuthorizationReference().equals("AUTH-450010"))
                .hasSize(1);
        assertThat(reloadAccount(fixture).getAvailableCredit())
                .isEqualByComparingTo(creditAfterFirstRequest)
                .isEqualByComparingTo("6874.25");
    }

    @Test
    void invalidRequestDoesNotPersistOrMutateCredit() throws Exception {
        Fixture fixture = saveActiveFixture("450011", "7000.00");
        AuthorizationCreateRequest request = new AuthorizationCreateRequest(
                " ", fixture.card().getCardReference(), fixture.merchant().getMerchantCode(),
                BigDecimal.ZERO, "usd", null, null
        );

        postAuthorization(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));

        assertThat(authorizationRepository.count()).isZero();
        assertThat(reloadAccount(fixture).getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    private org.springframework.test.web.servlet.ResultActions postAuthorization(
            AuthorizationCreateRequest request
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/authorizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
    }

    private void assertDeclinedWithoutCreditMutation(
            Fixture fixture,
            AuthorizationCreateRequest request
    ) throws Exception {
        BigDecimal availableBefore = fixture.cardAccount().getAvailableCredit();
        BigDecimal exposureBefore = committedExposure(fixture.cardAccount());
        Long versionBefore = fixture.cardAccount().getVersion();

        postAuthorization(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        Authorization persisted = authorizationRepository
                .findByAuthorizationReference(request.authorizationReference()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AuthorizationStatus.DECLINED);
        CardAccount reloaded = reloadAccount(fixture);
        assertThat(reloaded.getAvailableCredit()).isEqualByComparingTo(availableBefore);
        assertThat(committedExposure(reloaded)).isEqualByComparingTo(exposureBefore);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore);
    }

    private Fixture saveActiveFixture(String suffix, String availableCredit) {
        return saveFixture(suffix, CardStatus.ACTIVE, futureExpiration(),
                CardAccountStatus.ACTIVE, MerchantStatus.ACTIVE, availableCredit);
    }

    private Fixture saveFixture(
            String suffix,
            CardStatus cardStatus,
            YearMonth expiration,
            CardAccountStatus accountStatus,
            MerchantStatus merchantStatus,
            String availableCredit
    ) {
        CardAccount account = saveCardAccount(suffix, accountStatus, availableCredit);
        Card card = saveCard(suffix, cardStatus, expiration, account);
        Merchant merchant = saveMerchant(suffix, merchantStatus);
        return new Fixture(account, card, merchant);
    }

    private CardAccount saveCardAccount(
            String suffix,
            CardAccountStatus status,
            String availableCredit
    ) {
        return cardAccountRepository.saveAndFlush(new CardAccount(
                "ACC-" + suffix,
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal(availableCredit),
                "USD",
                status
        ));
    }

    private Card saveCard(
            String suffix,
            CardStatus status,
            YearMonth expiration,
            CardAccount account
    ) {
        return cardRepository.saveAndFlush(new Card(
                "CARD-" + suffix,
                "4242",
                expiration.getMonthValue(),
                expiration.getYear(),
                status,
                account
        ));
    }

    private Merchant saveMerchant(String suffix, MerchantStatus status) {
        return merchantRepository.saveAndFlush(new Merchant(
                "MER-" + suffix,
                "Integration Merchant " + suffix + " LLC",
                "Integration Merchant " + suffix,
                "5411",
                "US",
                status
        ));
    }

    private static AuthorizationCreateRequest request(
            String authorizationReference,
            Fixture fixture,
            String amount
    ) {
        return new AuthorizationCreateRequest(
                authorizationReference,
                fixture.card().getCardReference(),
                fixture.merchant().getMerchantCode(),
                new BigDecimal(amount),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.POS
        );
    }

    private CardAccount reloadAccount(Fixture fixture) {
        return reloadAccount(fixture.cardAccount());
    }

    private CardAccount reloadAccount(CardAccount account) {
        return cardAccountRepository.findById(account.getId()).orElseThrow();
    }

    private Long relatedId(String columnName, String authorizationReference) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM authorizations WHERE authorization_reference = ?",
                Long.class,
                authorizationReference
        );
    }

    private static BigDecimal committedExposure(CardAccount account) {
        return account.getCreditLimit().subtract(account.getAvailableCredit());
    }

    private static YearMonth futureExpiration() {
        return YearMonth.now(ZoneOffset.UTC).plusYears(2);
    }

    private record Fixture(CardAccount cardAccount, Card card, Merchant merchant) {
    }
}
