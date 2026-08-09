package com.hx.creditcardflow.authorization.service;

import com.hx.creditcardflow.authorization.dto.AuthorizationCreateRequest;
import com.hx.creditcardflow.authorization.dto.AuthorizationResponse;
import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.authorization.exception.DuplicateAuthorizationReferenceException;
import com.hx.creditcardflow.authorization.repository.AuthorizationRepository;
import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.card.exception.CardNotFoundException;
import com.hx.creditcardflow.card.repository.CardRepository;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.exception.MerchantNotFoundException;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @Test
    void eligibleAuthorizationIsApproved() {
        Authorization saved = authorize(eligibleFixture(), validRequest());

        assertThat(saved.getStatus()).isEqualTo(AuthorizationStatus.APPROVED);
    }

    @Test
    void approvedAuthorizationIsPersisted() {
        authorize(eligibleFixture(), validRequest());

        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    void approvedAuthorizationIncreasesCommittedExposureByExactAmount() {
        Fixture fixture = eligibleFixture();
        BigDecimal before = committedExposure(fixture.cardAccount());

        authorize(fixture, validRequest());

        assertThat(committedExposure(fixture.cardAccount()).subtract(before))
                .isEqualByComparingTo("125.75");
    }

    @Test
    void approvedAuthorizationPreservesAvailableCreditInvariant() {
        Fixture fixture = eligibleFixture();

        authorize(fixture, validRequest());

        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("6874.25");
        assertThat(fixture.cardAccount().getCreditLimit()
                .subtract(committedExposure(fixture.cardAccount())))
                .isEqualByComparingTo(fixture.cardAccount().getAvailableCredit());
    }

    @Test
    void insufficientAvailableCreditReturnsDeclined() {
        Fixture fixture = fixture(CardStatus.ACTIVE, futureExpiration(), CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE, "100.00");

        Authorization saved = authorize(fixture, validRequest());

        assertThat(saved.getStatus()).isEqualTo(AuthorizationStatus.DECLINED);
    }

    @Test
    void insufficientCreditDoesNotChangeCommittedExposure() {
        Fixture fixture = fixture(CardStatus.ACTIVE, futureExpiration(), CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE, "100.00");
        BigDecimal before = committedExposure(fixture.cardAccount());

        authorize(fixture, validRequest());

        assertThat(committedExposure(fixture.cardAccount())).isEqualByComparingTo(before);
    }

    @Test
    void nonActiveCardReturnsDeclined() {
        Fixture fixture = fixture(CardStatus.BLOCKED, futureExpiration(), CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE, "7000.00");

        assertThat(authorize(fixture, validRequest()).getStatus()).isEqualTo(AuthorizationStatus.DECLINED);
        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    @Test
    void expiredCardReturnsDeclined() {
        Fixture fixture = fixture(CardStatus.ACTIVE, YearMonth.now(ZoneOffset.UTC).minusMonths(1),
                CardAccountStatus.ACTIVE, MerchantStatus.ACTIVE, "7000.00");

        assertThat(authorize(fixture, validRequest()).getStatus()).isEqualTo(AuthorizationStatus.DECLINED);
    }

    @Test
    void nonActiveCardAccountReturnsDeclined() {
        Fixture fixture = fixture(CardStatus.ACTIVE, futureExpiration(), CardAccountStatus.SUSPENDED,
                MerchantStatus.ACTIVE, "7000.00");

        assertThat(authorize(fixture, validRequest()).getStatus()).isEqualTo(AuthorizationStatus.DECLINED);
    }

    @Test
    void nonActiveMerchantReturnsDeclined() {
        Fixture fixture = fixture(CardStatus.ACTIVE, futureExpiration(), CardAccountStatus.ACTIVE,
                MerchantStatus.SUSPENDED, "7000.00");

        assertThat(authorize(fixture, validRequest()).getStatus()).isEqualTo(AuthorizationStatus.DECLINED);
    }

    @Test
    void normalDeclinedAuthorizationIsStillPersisted() {
        Fixture fixture = fixture(CardStatus.CLOSED, futureExpiration(), CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE, "7000.00");

        Authorization saved = authorize(fixture, validRequest());

        verify(authorizationRepository).save(saved);
    }

    @Test
    void missingCardUsesExistingNotFoundBehavior() {
        AuthorizationCreateRequest request = validRequest();
        when(authorizationRepository.findByAuthorizationReference(request.authorizationReference()))
                .thenReturn(Optional.empty());
        when(cardRepository.findByCardReference(request.cardReference())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.createAuthorization(request))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessage("Card not found with card reference: CARD-430001");
        verify(authorizationRepository, never()).save(any(Authorization.class));
    }

    @Test
    void missingMerchantUsesExistingNotFoundBehavior() {
        AuthorizationCreateRequest request = validRequest();
        Fixture fixture = eligibleFixture();
        when(authorizationRepository.findByAuthorizationReference(request.authorizationReference()))
                .thenReturn(Optional.empty());
        when(cardRepository.findByCardReference(request.cardReference())).thenReturn(Optional.of(fixture.card()));
        when(merchantRepository.findByMerchantCode(request.merchantCode())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.createAuthorization(request))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessage("Merchant not found with merchant code: MER-430001");
        verify(authorizationRepository, never()).save(any(Authorization.class));
    }

    @Test
    void duplicateAuthorizationReferenceUsesConflictConvention() {
        AuthorizationCreateRequest request = validRequest();
        Fixture fixture = eligibleFixture();
        Authorization existing = authorization(request, fixture, AuthorizationStatus.APPROVED);
        when(authorizationRepository.findByAuthorizationReference(request.authorizationReference()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authorizationService.createAuthorization(request))
                .isInstanceOf(DuplicateAuthorizationReferenceException.class)
                .hasMessage("Authorization reference already exists: AUTH-430001");
        verify(cardRepository, never()).findByCardReference(any(String.class));
        verify(authorizationRepository, never()).save(any(Authorization.class));
    }

    @Test
    void savedAuthorizationHasExpectedCardAndMerchantRelationships() {
        Fixture fixture = eligibleFixture();

        Authorization saved = authorize(fixture, validRequest());

        assertThat(saved.getCard()).isSameAs(fixture.card());
        assertThat(saved.getMerchant()).isSameAs(fixture.merchant());
    }

    @Test
    void getExistingAuthorizationReturnsExpectedResponse() {
        Fixture fixture = eligibleFixture();
        Authorization authorization = authorization(validRequest(), fixture, AuthorizationStatus.APPROVED);
        when(authorizationRepository.findByAuthorizationReference("AUTH-430001"))
                .thenReturn(Optional.of(authorization));

        AuthorizationResponse response = authorizationService.getAuthorization("AUTH-430001");

        assertThat(response.authorizationReference()).isEqualTo("AUTH-430001");
        assertThat(response.cardReference()).isEqualTo("CARD-430001");
        assertThat(response.merchantCode()).isEqualTo("MER-430001");
        assertThat(response.status()).isEqualTo(AuthorizationStatus.APPROVED);
    }

    @Test
    void getMissingAuthorizationThrowsNotFoundException() {
        when(authorizationRepository.findByAuthorizationReference("AUTH-NOT-FOUND"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.getAuthorization("AUTH-NOT-FOUND"))
                .isInstanceOf(AuthorizationNotFoundException.class)
                .hasMessage("Authorization not found with authorization reference: AUTH-NOT-FOUND");
    }

    private Authorization authorize(Fixture fixture, AuthorizationCreateRequest request) {
        when(authorizationRepository.findByAuthorizationReference(request.authorizationReference()))
                .thenReturn(Optional.empty());
        when(cardRepository.findByCardReference(request.cardReference())).thenReturn(Optional.of(fixture.card()));
        when(merchantRepository.findByMerchantCode(request.merchantCode()))
                .thenReturn(Optional.of(fixture.merchant()));
        when(authorizationRepository.save(any(Authorization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizationResponse response = authorizationService.createAuthorization(request);

        ArgumentCaptor<Authorization> captor = ArgumentCaptor.forClass(Authorization.class);
        verify(authorizationRepository).save(captor.capture());
        assertThat(response.status()).isEqualTo(captor.getValue().getStatus());
        return captor.getValue();
    }

    private static Fixture eligibleFixture() {
        return fixture(CardStatus.ACTIVE, futureExpiration(), CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE, "7000.00");
    }

    private static Fixture fixture(
            CardStatus cardStatus,
            YearMonth expiration,
            CardAccountStatus accountStatus,
            MerchantStatus merchantStatus,
            String availableCredit
    ) {
        CardAccount cardAccount = new CardAccount(
                "ACC-430001",
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal(availableCredit),
                "USD",
                accountStatus
        );
        Card card = new Card(
                "CARD-430001",
                "4242",
                expiration.getMonthValue(),
                expiration.getYear(),
                cardStatus,
                cardAccount
        );
        Merchant merchant = new Merchant(
                "MER-430001",
                "Authorization Merchant LLC",
                "Authorization Merchant",
                "5411",
                "US",
                merchantStatus
        );
        return new Fixture(cardAccount, card, merchant);
    }

    private static AuthorizationCreateRequest validRequest() {
        return new AuthorizationCreateRequest(
                "AUTH-430001",
                "CARD-430001",
                "MER-430001",
                new BigDecimal("125.75"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.POS
        );
    }

    private static Authorization authorization(
            AuthorizationCreateRequest request,
            Fixture fixture,
            AuthorizationStatus status
    ) {
        return new Authorization(
                request.authorizationReference(),
                fixture.card(),
                fixture.merchant(),
                request.amount(),
                request.currencyCode(),
                request.authorizationType(),
                request.channel(),
                status
        );
    }

    private static BigDecimal committedExposure(CardAccount cardAccount) {
        return cardAccount.getCreditLimit().subtract(cardAccount.getAvailableCredit());
    }

    private static YearMonth futureExpiration() {
        return YearMonth.now(ZoneOffset.UTC).plusYears(2);
    }

    private record Fixture(CardAccount cardAccount, Card card, Merchant merchant) {
    }
}
