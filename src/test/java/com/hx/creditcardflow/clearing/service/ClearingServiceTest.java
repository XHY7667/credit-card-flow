package com.hx.creditcardflow.clearing.service;

import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.authorization.repository.AuthorizationRepository;
import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.clearing.dto.ClearingCreateRequest;
import com.hx.creditcardflow.clearing.dto.ClearingResponse;
import com.hx.creditcardflow.clearing.entity.Clearing;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.exception.ClearingAmountMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingCurrencyMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingNotAllowedException;
import com.hx.creditcardflow.clearing.exception.DuplicateClearingReferenceException;
import com.hx.creditcardflow.clearing.repository.ClearingRepository;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearingServiceTest {

    @Mock
    private ClearingRepository clearingRepository;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @InjectMocks
    private ClearingService clearingService;

    @Test
    void approvedAuthorizationWithExactAmountAndCurrencyCreatesPostedClearing() {
        ClearingResponse response = clear(activeFixture(AuthorizationStatus.APPROVED), validRequest());

        assertThat(response.status()).isEqualTo(ClearingStatus.POSTED);
        assertThat(response.clearingReference()).isEqualTo("CLR-630001");
    }

    @Test
    void successfulClearingIsPersisted() {
        clear(activeFixture(AuthorizationStatus.APPROVED), validRequest());

        verify(clearingRepository).save(any(Clearing.class));
    }

    @Test
    void successfulClearingChangesAuthorizationToCleared() {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);

        clear(fixture, validRequest());

        assertThat(fixture.authorization().getStatus()).isEqualTo(AuthorizationStatus.CLEARED);
        verify(authorizationRepository).save(fixture.authorization());
    }

    @Test
    void successfulClearingIncreasesCurrentBalanceAndLeavesAvailableCreditUnchanged() {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);

        clear(fixture, validRequest());

        assertThat(fixture.cardAccount().getCurrentBalance()).isEqualByComparingTo("2125.75");
        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("6874.25");
    }

    @Test
    void successfulClearingPreservesTotalExposureAndReducesPendingExposure() {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);
        BigDecimal totalBefore = totalExposure(fixture.cardAccount());
        BigDecimal pendingBefore = pendingExposure(fixture.cardAccount());

        clear(fixture, validRequest());

        assertThat(totalExposure(fixture.cardAccount())).isEqualByComparingTo(totalBefore);
        assertThat(pendingBefore).isEqualByComparingTo("1125.75");
        assertThat(pendingExposure(fixture.cardAccount())).isEqualByComparingTo("1000.00");
    }

    @Test
    void savedClearingReferencesExpectedAuthorization() {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);
        clear(fixture, validRequest());
        ArgumentCaptor<Clearing> captor = ArgumentCaptor.forClass(Clearing.class);

        verify(clearingRepository).save(captor.capture());

        assertThat(captor.getValue().getAuthorization()).isSameAs(fixture.authorization());
    }

    @Test
    void missingAuthorizationUsesExistingNotFoundBehavior() {
        when(clearingRepository.findByClearingReference("CLR-630001"))
                .thenReturn(Optional.empty());
        when(authorizationRepository.findByAuthorizationReference("AUTH-630001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> clearingService.createClearing(validRequest()))
                .isInstanceOf(AuthorizationNotFoundException.class)
                .hasMessage("Authorization not found with authorization reference: AUTH-630001");
        verify(clearingRepository, never()).save(any(Clearing.class));
    }

    @Test
    void duplicateClearingReferenceIsRejectedBeforeAuthorizationLookup() {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);
        when(clearingRepository.findByClearingReference("CLR-630001"))
                .thenReturn(Optional.of(clearing(fixture.authorization())));

        assertThatThrownBy(() -> clearingService.createClearing(validRequest()))
                .isInstanceOf(DuplicateClearingReferenceException.class)
                .hasMessage("Clearing reference already exists: CLR-630001");
        verify(authorizationRepository, never()).findByAuthorizationReference(any(String.class));
    }

    @Test
    void pendingAuthorizationCannotBeCleared() {
        assertStatusRejected(AuthorizationStatus.PENDING);
    }

    @Test
    void declinedAuthorizationCannotBeCleared() {
        assertStatusRejected(AuthorizationStatus.DECLINED);
    }

    @Test
    void reversedAuthorizationCannotBeCleared() {
        assertStatusRejected(AuthorizationStatus.REVERSED);
    }

    @Test
    void alreadyClearedAuthorizationCannotBeClearedAgain() {
        assertStatusRejected(AuthorizationStatus.CLEARED);
    }

    @Test
    void smallerClearingAmountIsRejectedWithoutFinancialMutationOrPersistence() {
        assertAmountRejected("125.74");
    }

    @Test
    void largerClearingAmountIsRejectedWithoutFinancialMutationOrPersistence() {
        assertAmountRejected("125.76");
    }

    @Test
    void numericallyEqualAmountsWithDifferentScalesAreAccepted() {
        ClearingCreateRequest request = request("125.750", "USD");

        ClearingResponse response = clear(activeFixture(AuthorizationStatus.APPROVED), request);

        assertThat(response.status()).isEqualTo(ClearingStatus.POSTED);
    }

    @Test
    void currencyMismatchIsRejectedWithoutFinancialMutationOrPersistence() {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);
        stubLookup(fixture);

        assertThatThrownBy(() -> clearingService.createClearing(request("125.75", "EUR")))
                .isInstanceOf(ClearingCurrencyMismatchException.class)
                .hasMessage("Clearing currency EUR must equal authorization currency USD");
        assertUnchanged(fixture);
        verify(clearingRepository, never()).save(any(Clearing.class));
    }

    @Test
    void currentCardAccountAndMerchantStatusesAndCardEligibilityDoNotBlockClearing() {
        Fixture fixture = fixture(
                AuthorizationStatus.APPROVED,
                CardStatus.BLOCKED,
                1,
                2020,
                CardAccountStatus.SUSPENDED,
                MerchantStatus.SUSPENDED
        );

        ClearingResponse response = clear(fixture, validRequest());

        assertThat(response.status()).isEqualTo(ClearingStatus.POSTED);
        assertThat(fixture.cardAccount().getCurrentBalance()).isEqualByComparingTo("2125.75");
    }

    private ClearingResponse clear(Fixture fixture, ClearingCreateRequest request) {
        stubLookup(fixture);
        when(authorizationRepository.save(fixture.authorization()))
                .thenReturn(fixture.authorization());
        when(clearingRepository.save(any(Clearing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        return clearingService.createClearing(request);
    }

    private void stubLookup(Fixture fixture) {
        when(clearingRepository.findByClearingReference("CLR-630001"))
                .thenReturn(Optional.empty());
        when(authorizationRepository.findByAuthorizationReference("AUTH-630001"))
                .thenReturn(Optional.of(fixture.authorization()));
    }

    private void assertStatusRejected(AuthorizationStatus status) {
        Fixture fixture = activeFixture(status);
        stubLookup(fixture);

        assertThatThrownBy(() -> clearingService.createClearing(validRequest()))
                .isInstanceOf(ClearingNotAllowedException.class)
                .hasMessage("Authorization cannot be cleared: AUTH-630001 with status " + status);
        assertFinancialUnchanged(fixture);
        assertThat(fixture.authorization().getStatus()).isEqualTo(status);
        verify(clearingRepository, never()).save(any(Clearing.class));
    }

    private void assertAmountRejected(String amount) {
        Fixture fixture = activeFixture(AuthorizationStatus.APPROVED);
        stubLookup(fixture);

        assertThatThrownBy(() -> clearingService.createClearing(request(amount, "USD")))
                .isInstanceOf(ClearingAmountMismatchException.class)
                .hasMessage("Clearing amount " + amount + " must equal authorization amount 125.75");
        assertUnchanged(fixture);
        verify(clearingRepository, never()).save(any(Clearing.class));
    }

    private static void assertUnchanged(Fixture fixture) {
        assertFinancialUnchanged(fixture);
        assertThat(fixture.authorization().getStatus()).isNotEqualTo(AuthorizationStatus.CLEARED);
    }

    private static void assertFinancialUnchanged(Fixture fixture) {
        assertThat(fixture.cardAccount().getCurrentBalance()).isEqualByComparingTo("2000.00");
        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("6874.25");
    }

    private static Fixture activeFixture(AuthorizationStatus status) {
        return fixture(
                status,
                CardStatus.ACTIVE,
                12,
                2030,
                CardAccountStatus.ACTIVE,
                MerchantStatus.ACTIVE
        );
    }

    private static Fixture fixture(
            AuthorizationStatus authorizationStatus,
            CardStatus cardStatus,
            int expirationMonth,
            int expirationYear,
            CardAccountStatus accountStatus,
            MerchantStatus merchantStatus
    ) {
        CardAccount cardAccount = new CardAccount(
                "ACC-630001", new BigDecimal("10000.00"), new BigDecimal("2000.00"),
                new BigDecimal("6874.25"), "USD", accountStatus
        );
        Card card = new Card(
                "CARD-630001", "4242", expirationMonth, expirationYear, cardStatus, cardAccount
        );
        Merchant merchant = new Merchant(
                "MER-630001", "Clearing Merchant LLC", "Clearing Merchant",
                "5411", "US", merchantStatus
        );
        Authorization authorization = new Authorization(
                "AUTH-630001", card, merchant, new BigDecimal("125.75"), "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS, authorizationStatus
        );
        return new Fixture(cardAccount, authorization);
    }

    private static ClearingCreateRequest validRequest() {
        return request("125.75", "USD");
    }

    private static ClearingCreateRequest request(String amount, String currencyCode) {
        return new ClearingCreateRequest(
                "CLR-630001", "AUTH-630001", new BigDecimal(amount), currencyCode
        );
    }

    private static Clearing clearing(Authorization authorization) {
        return new Clearing(
                "CLR-630001", authorization, new BigDecimal("125.75"),
                "USD", ClearingStatus.POSTED
        );
    }

    private static BigDecimal totalExposure(CardAccount cardAccount) {
        return cardAccount.getCreditLimit().subtract(cardAccount.getAvailableCredit());
    }

    private static BigDecimal pendingExposure(CardAccount cardAccount) {
        return totalExposure(cardAccount).subtract(cardAccount.getCurrentBalance());
    }

    private record Fixture(CardAccount cardAccount, Authorization authorization) {
    }
}
