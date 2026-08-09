package com.hx.creditcardflow.reversal.service;

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
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.reversal.dto.ReversalCreateRequest;
import com.hx.creditcardflow.reversal.dto.ReversalResponse;
import com.hx.creditcardflow.reversal.entity.AuthorizationReversal;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import com.hx.creditcardflow.reversal.exception.DuplicateReversalReferenceException;
import com.hx.creditcardflow.reversal.exception.ReversalAmountMismatchException;
import com.hx.creditcardflow.reversal.exception.ReversalNotAllowedException;
import com.hx.creditcardflow.reversal.repository.AuthorizationReversalRepository;
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
class ReversalServiceTest {

    @Mock
    private AuthorizationReversalRepository reversalRepository;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @InjectMocks
    private ReversalService reversalService;

    @Test
    void approvedAuthorizationWithFullAmountCreatesCompletedReversal() {
        AuthorizationReversal saved = reverse(fixture(AuthorizationStatus.APPROVED), validRequest());

        assertThat(saved.getStatus()).isEqualTo(ReversalStatus.COMPLETED);
    }

    @Test
    void successfulReversalIsPersisted() {
        reverse(fixture(AuthorizationStatus.APPROVED), validRequest());

        verify(reversalRepository).save(any(AuthorizationReversal.class));
    }

    @Test
    void successfulReversalChangesAuthorizationToReversed() {
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);

        reverse(fixture, validRequest());

        assertThat(fixture.authorization().getStatus()).isEqualTo(AuthorizationStatus.REVERSED);
        verify(authorizationRepository).save(fixture.authorization());
    }

    @Test
    void successfulReversalReleasesExactAuthorizationAmount() {
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);

        reverse(fixture, validRequest());

        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    @Test
    void successfulReversalPreservesCardAccountMonetaryInvariant() {
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);

        reverse(fixture, validRequest());

        assertThat(fixture.cardAccount().getCreditLimit()).isEqualByComparingTo("10000.00");
        assertThat(fixture.cardAccount().getCurrentBalance()).isEqualByComparingTo("2000.00");
        assertThat(committedExposure(fixture.cardAccount())).isEqualByComparingTo("3000.00");
    }

    @Test
    void savedReversalReferencesExpectedAuthorization() {
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);

        AuthorizationReversal saved = reverse(fixture, validRequest());

        assertThat(saved.getAuthorization()).isSameAs(fixture.authorization());
    }

    @Test
    void missingAuthorizationUsesExistingNotFoundBehavior() {
        ReversalCreateRequest request = validRequest();
        when(reversalRepository.findByReversalReference(request.reversalReference()))
                .thenReturn(Optional.empty());
        when(authorizationRepository.findByAuthorizationReference(request.authorizationReference()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reversalService.createReversal(request))
                .isInstanceOf(AuthorizationNotFoundException.class)
                .hasMessage("Authorization not found with authorization reference: AUTH-530001");
        verify(reversalRepository, never()).save(any(AuthorizationReversal.class));
    }

    @Test
    void duplicateReversalReferenceIsRejected() {
        ReversalCreateRequest request = validRequest();
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);
        when(reversalRepository.findByReversalReference(request.reversalReference()))
                .thenReturn(Optional.of(reversal(request, fixture.authorization())));

        assertThatThrownBy(() -> reversalService.createReversal(request))
                .isInstanceOf(DuplicateReversalReferenceException.class)
                .hasMessage("Reversal reference already exists: REV-530001");
        verify(authorizationRepository, never()).findByAuthorizationReference(any(String.class));
        verify(reversalRepository, never()).save(any(AuthorizationReversal.class));
    }

    @Test
    void declinedAuthorizationCannotBeReversed() {
        assertStatusRejected(AuthorizationStatus.DECLINED);
    }

    @Test
    void pendingAuthorizationCannotBeReversed() {
        assertStatusRejected(AuthorizationStatus.PENDING);
    }

    @Test
    void alreadyReversedAuthorizationCannotBeReversed() {
        assertStatusRejected(AuthorizationStatus.REVERSED);
    }

    @Test
    void smallerReversalAmountIsRejected() {
        assertAmountRejected("125.74");
    }

    @Test
    void largerReversalAmountIsRejected() {
        assertAmountRejected("125.76");
    }

    @Test
    void numericallyEqualAmountsWithDifferentScalesAreAccepted() {
        ReversalCreateRequest request = new ReversalCreateRequest(
                "REV-530001", "AUTH-530001", new BigDecimal("125.750")
        );

        AuthorizationReversal saved = reverse(fixture(AuthorizationStatus.APPROVED), request);

        assertThat(saved.getStatus()).isEqualTo(ReversalStatus.COMPLETED);
    }

    @Test
    void rejectedReversalDoesNotMutateAvailableCredit() {
        Fixture fixture = fixture(AuthorizationStatus.DECLINED);
        stubLookup(fixture, validRequest());

        assertThatThrownBy(() -> reversalService.createReversal(validRequest()))
                .isInstanceOf(ReversalNotAllowedException.class);
        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("6874.25");
    }

    @Test
    void rejectedReversalIsNotPersisted() {
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);
        ReversalCreateRequest request = requestWithAmount("100.00");
        stubLookup(fixture, request);

        assertThatThrownBy(() -> reversalService.createReversal(request))
                .isInstanceOf(ReversalAmountMismatchException.class);
        verify(reversalRepository, never()).save(any(AuthorizationReversal.class));
    }

    @Test
    void currentCardAccountAndMerchantStatusesDoNotBlockValidReversal() {
        Fixture fixture = fixture(
                AuthorizationStatus.APPROVED,
                CardStatus.BLOCKED,
                CardAccountStatus.SUSPENDED,
                MerchantStatus.SUSPENDED
        );

        AuthorizationReversal saved = reverse(fixture, validRequest());

        assertThat(saved.getStatus()).isEqualTo(ReversalStatus.COMPLETED);
        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    private AuthorizationReversal reverse(Fixture fixture, ReversalCreateRequest request) {
        stubLookup(fixture, request);
        when(authorizationRepository.save(fixture.authorization())).thenReturn(fixture.authorization());
        when(reversalRepository.save(any(AuthorizationReversal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReversalResponse response = reversalService.createReversal(request);

        ArgumentCaptor<AuthorizationReversal> captor = ArgumentCaptor.forClass(AuthorizationReversal.class);
        verify(reversalRepository).save(captor.capture());
        assertThat(response.status()).isEqualTo(captor.getValue().getStatus());
        return captor.getValue();
    }

    private void stubLookup(Fixture fixture, ReversalCreateRequest request) {
        when(reversalRepository.findByReversalReference(request.reversalReference()))
                .thenReturn(Optional.empty());
        when(authorizationRepository.findByAuthorizationReference(request.authorizationReference()))
                .thenReturn(Optional.of(fixture.authorization()));
    }

    private void assertStatusRejected(AuthorizationStatus status) {
        Fixture fixture = fixture(status);
        ReversalCreateRequest request = validRequest();
        stubLookup(fixture, request);

        assertThatThrownBy(() -> reversalService.createReversal(request))
                .isInstanceOf(ReversalNotAllowedException.class)
                .hasMessage("Authorization cannot be reversed: AUTH-530001 with status " + status);
        verify(reversalRepository, never()).save(any(AuthorizationReversal.class));
    }

    private void assertAmountRejected(String amount) {
        Fixture fixture = fixture(AuthorizationStatus.APPROVED);
        ReversalCreateRequest request = requestWithAmount(amount);
        stubLookup(fixture, request);

        assertThatThrownBy(() -> reversalService.createReversal(request))
                .isInstanceOf(ReversalAmountMismatchException.class)
                .hasMessage("Reversal amount " + amount + " must equal authorization amount 125.75");
        assertThat(fixture.cardAccount().getAvailableCredit()).isEqualByComparingTo("6874.25");
        verify(reversalRepository, never()).save(any(AuthorizationReversal.class));
    }

    private static Fixture fixture(AuthorizationStatus status) {
        return fixture(status, CardStatus.ACTIVE, CardAccountStatus.ACTIVE, MerchantStatus.ACTIVE);
    }

    private static Fixture fixture(
            AuthorizationStatus status,
            CardStatus cardStatus,
            CardAccountStatus cardAccountStatus,
            MerchantStatus merchantStatus
    ) {
        CardAccount cardAccount = new CardAccount(
                "ACC-530001",
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("6874.25"),
                "USD",
                cardAccountStatus
        );
        Card card = new Card(
                "CARD-530001",
                "4242",
                12,
                2030,
                cardStatus,
                cardAccount
        );
        Merchant merchant = new Merchant(
                "MER-530001",
                "Reversal Merchant LLC",
                "Reversal Merchant",
                "5411",
                "US",
                merchantStatus
        );
        Authorization authorization = new Authorization(
                "AUTH-530001",
                card,
                merchant,
                new BigDecimal("125.75"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.POS,
                status
        );
        return new Fixture(cardAccount, authorization);
    }

    private static ReversalCreateRequest validRequest() {
        return requestWithAmount("125.75");
    }

    private static ReversalCreateRequest requestWithAmount(String amount) {
        return new ReversalCreateRequest(
                "REV-530001",
                "AUTH-530001",
                new BigDecimal(amount)
        );
    }

    private static AuthorizationReversal reversal(
            ReversalCreateRequest request,
            Authorization authorization
    ) {
        return new AuthorizationReversal(
                request.reversalReference(),
                authorization,
                request.amount(),
                ReversalStatus.COMPLETED
        );
    }

    private static BigDecimal committedExposure(CardAccount cardAccount) {
        return cardAccount.getCreditLimit().subtract(cardAccount.getAvailableCredit());
    }

    private record Fixture(CardAccount cardAccount, Authorization authorization) {
    }
}
