package com.hx.creditcardflow.card.service;

import com.hx.creditcardflow.card.dto.CardCreateRequest;
import com.hx.creditcardflow.card.dto.CardResponse;
import com.hx.creditcardflow.card.dto.CardUpdateRequest;
import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.card.exception.CardAccountNotEligibleForCardIssuanceException;
import com.hx.creditcardflow.card.exception.CardNotFoundException;
import com.hx.creditcardflow.card.exception.DuplicateCardReferenceException;
import com.hx.creditcardflow.card.exception.InvalidCardExpirationException;
import com.hx.creditcardflow.card.repository.CardRepository;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.exception.CardAccountNotFoundException;
import com.hx.creditcardflow.cardaccount.repository.CardAccountRepository;
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
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardAccountRepository cardAccountRepository;

    @InjectMocks
    private CardService cardService;

    @Test
    void successfulIssuanceInitializesStatusToActive() {
        Card savedCard = issueCard(validCreateRequest());

        assertThat(savedCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void successfulIssuanceAssociatesExpectedCardAccount() {
        Card savedCard = issueCard(validCreateRequest());

        assertThat(savedCard.getCardAccount().getAccountNumber()).isEqualTo("ACC-311001");
    }

    @Test
    void duplicateCardReferenceIsRejectedWithoutSaving() {
        CardCreateRequest request = validCreateRequest();
        when(cardRepository.existsByCardReference("CARD-311001")).thenReturn(true);

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(DuplicateCardReferenceException.class)
                .hasMessage("Card reference already exists: CARD-311001");
        verify(cardRepository, never()).save(any(Card.class));
        verify(cardAccountRepository, never()).findByAccountNumber(any(String.class));
    }

    @Test
    void missingCardAccountUsesExistingNotFoundExceptionWithoutSaving() {
        CardCreateRequest request = validCreateRequest();
        when(cardRepository.existsByCardReference("CARD-311001")).thenReturn(false);
        when(cardAccountRepository.findByAccountNumber("ACC-311001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(CardAccountNotFoundException.class)
                .hasMessage("Card account not found with account number: ACC-311001");
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void suspendedCardAccountCannotReceiveNewCard() {
        assertIneligibleAccountIsRejected(CardAccountStatus.SUSPENDED);
    }

    @Test
    void delinquentCardAccountCannotReceiveNewCard() {
        assertIneligibleAccountIsRejected(CardAccountStatus.DELINQUENT);
    }

    @Test
    void closedCardAccountCannotReceiveNewCard() {
        assertIneligibleAccountIsRejected(CardAccountStatus.CLOSED);
    }

    @Test
    void pastExpirationIsRejectedWithoutSaving() {
        YearMonth past = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        CardCreateRequest request = requestWithExpiration(past);
        stubActiveAccount(request);

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(InvalidCardExpirationException.class)
                .hasMessage("Card expiration is before the current month: " + past);
        verify(cardRepository, never()).save(any(Card.class));
    }

    @Test
    void currentMonthExpirationIsAccepted() {
        Card savedCard = issueCard(requestWithExpiration(YearMonth.now(ZoneOffset.UTC)));

        assertThat(YearMonth.of(savedCard.getExpirationYear(), savedCard.getExpirationMonth()))
                .isEqualTo(YearMonth.now(ZoneOffset.UTC));
    }

    @Test
    void futureExpirationIsAccepted() {
        YearMonth future = YearMonth.now(ZoneOffset.UTC).plusYears(2);
        Card savedCard = issueCard(requestWithExpiration(future));

        assertThat(YearMonth.of(savedCard.getExpirationYear(), savedCard.getExpirationMonth()))
                .isEqualTo(future);
    }

    @Test
    void existingCardReturnsResponseIncludingCardAccountNumber() {
        Card card = existingCard(CardStatus.ACTIVE);
        when(cardRepository.findByCardReference("CARD-311002")).thenReturn(Optional.of(card));

        CardResponse response = cardService.getCard("CARD-311002");

        assertThat(response.cardReference()).isEqualTo("CARD-311002");
        assertThat(response.lastFour()).isEqualTo("0001");
        assertThat(response.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.cardAccountNumber()).isEqualTo("ACC-311002");
    }

    @Test
    void missingCardThrowsCardNotFoundException() {
        when(cardRepository.findByCardReference("CARD-NOT-FOUND"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCard("CARD-NOT-FOUND"))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessage("Card not found with card reference: CARD-NOT-FOUND");
    }

    @Test
    void updateChangesStatusOnExistingCardAndSavesIt() {
        Card card = existingCard(CardStatus.ACTIVE);
        when(cardRepository.findByCardReference("CARD-311002")).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);

        cardService.updateCard("CARD-311002", new CardUpdateRequest(CardStatus.BLOCKED));

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(card);
        assertThat(captor.getValue().getStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    private Card issueCard(CardCreateRequest request) {
        stubActiveAccount(request);
        when(cardRepository.save(any(Card.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        cardService.createCard(request);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        return captor.getValue();
    }

    private void stubActiveAccount(CardCreateRequest request) {
        when(cardRepository.existsByCardReference(request.cardReference())).thenReturn(false);
        when(cardAccountRepository.findByAccountNumber(request.cardAccountNumber()))
                .thenReturn(Optional.of(cardAccount(request.cardAccountNumber(), CardAccountStatus.ACTIVE)));
    }

    private void assertIneligibleAccountIsRejected(CardAccountStatus status) {
        CardCreateRequest request = validCreateRequest();
        when(cardRepository.existsByCardReference(request.cardReference())).thenReturn(false);
        when(cardAccountRepository.findByAccountNumber(request.cardAccountNumber()))
                .thenReturn(Optional.of(cardAccount(request.cardAccountNumber(), status)));

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(CardAccountNotEligibleForCardIssuanceException.class)
                .hasMessage("Card account is not eligible for card issuance: ACC-311001 with status " + status);
        verify(cardRepository, never()).save(any(Card.class));
    }

    private static CardCreateRequest validCreateRequest() {
        return requestWithExpiration(YearMonth.now(ZoneOffset.UTC).plusYears(2));
    }

    private static CardCreateRequest requestWithExpiration(YearMonth expiration) {
        return new CardCreateRequest(
                "CARD-311001",
                "4242",
                expiration.getMonthValue(),
                expiration.getYear(),
                "ACC-311001"
        );
    }

    private static Card existingCard(CardStatus status) {
        YearMonth expiration = YearMonth.now(ZoneOffset.UTC).plusYears(2);
        return new Card(
                "CARD-311002",
                "0001",
                expiration.getMonthValue(),
                expiration.getYear(),
                status,
                cardAccount("ACC-311002", CardAccountStatus.ACTIVE)
        );
    }

    private static CardAccount cardAccount(String accountNumber, CardAccountStatus status) {
        return new CardAccount(
                accountNumber,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                "USD",
                status
        );
    }
}
