package com.hx.creditcardflow.cardaccount.service;

import com.hx.creditcardflow.cardaccount.dto.CardAccountCreateRequest;
import com.hx.creditcardflow.cardaccount.dto.CardAccountResponse;
import com.hx.creditcardflow.cardaccount.dto.CardAccountUpdateRequest;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.exception.CardAccountNotFoundException;
import com.hx.creditcardflow.cardaccount.exception.DuplicateCardAccountNumberException;
import com.hx.creditcardflow.cardaccount.exception.InvalidCardAccountCreditLimitException;
import com.hx.creditcardflow.cardaccount.repository.CardAccountRepository;
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
class CardAccountServiceTest {

    @Mock
    private CardAccountRepository cardAccountRepository;

    @InjectMocks
    private CardAccountService cardAccountService;

    @Test
    void creationInitializesCurrentBalanceToZero() {
        CardAccount created = createCardAccount();

        assertThat(created.getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void creationInitializesAvailableCreditToCreditLimit() {
        CardAccount created = createCardAccount();

        assertThat(created.getAvailableCredit()).isEqualByComparingTo(created.getCreditLimit());
    }

    @Test
    void creationInitializesStatusToActive() {
        CardAccount created = createCardAccount();

        assertThat(created.getStatus()).isEqualTo(CardAccountStatus.ACTIVE);
    }

    @Test
    void duplicateAccountNumberIsRejectedWithoutSaving() {
        CardAccountCreateRequest request = createRequest();
        when(cardAccountRepository.existsByAccountNumber("ACC-340001")).thenReturn(true);

        assertThatThrownBy(() -> cardAccountService.createCardAccount(request))
                .isInstanceOf(DuplicateCardAccountNumberException.class)
                .hasMessage("Card account number already exists: ACC-340001");
        verify(cardAccountRepository, never()).save(any(CardAccount.class));
    }

    @Test
    void existingAccountIsReturned() {
        CardAccount cardAccount = accountWithExposure();
        when(cardAccountRepository.findByAccountNumber("ACC-340002"))
                .thenReturn(Optional.of(cardAccount));

        CardAccountResponse response = cardAccountService.getCardAccount("ACC-340002");

        assertThat(response.accountNumber()).isEqualTo("ACC-340002");
        assertThat(response.creditLimit()).isEqualByComparingTo("10000.00");
        assertThat(response.availableCredit()).isEqualByComparingTo("7000.00");
    }

    @Test
    void nonexistentAccountThrowsNotFoundException() {
        when(cardAccountRepository.findByAccountNumber("ACC-NOT-FOUND"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardAccountService.getCardAccount("ACC-NOT-FOUND"))
                .isInstanceOf(CardAccountNotFoundException.class)
                .hasMessage("Card account not found with account number: ACC-NOT-FOUND");
    }

    @Test
    void increasingCreditLimitPreservesCommittedExposure() {
        CardAccount updated = updateAccount(new BigDecimal("15000.00"), CardAccountStatus.ACTIVE);

        assertThat(updated.getCreditLimit()).isEqualByComparingTo("15000.00");
        assertThat(updated.getAvailableCredit()).isEqualByComparingTo("12000.00");
    }

    @Test
    void validCreditLimitReductionPreservesCommittedExposure() {
        CardAccount updated = updateAccount(new BigDecimal("5000.00"), CardAccountStatus.ACTIVE);

        assertThat(updated.getCreditLimit()).isEqualByComparingTo("5000.00");
        assertThat(updated.getAvailableCredit()).isEqualByComparingTo("2000.00");
    }

    @Test
    void creditLimitBelowCommittedExposureIsRejectedWithoutSaving() {
        CardAccount cardAccount = accountWithExposure();
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("2999.99"), CardAccountStatus.ACTIVE
        );
        when(cardAccountRepository.findByAccountNumber("ACC-340002"))
                .thenReturn(Optional.of(cardAccount));

        assertThatThrownBy(() -> cardAccountService.updateCardAccount("ACC-340002", request))
                .isInstanceOf(InvalidCardAccountCreditLimitException.class)
                .hasMessage("Card account credit limit 2999.99 cannot be below committed exposure 3000.00");
        verify(cardAccountRepository, never()).save(any(CardAccount.class));
    }

    @Test
    void updateAppliesRequestedStatus() {
        CardAccount updated = updateAccount(new BigDecimal("10000.00"), CardAccountStatus.SUSPENDED);

        assertThat(updated.getStatus()).isEqualTo(CardAccountStatus.SUSPENDED);
    }

    private CardAccount createCardAccount() {
        when(cardAccountRepository.existsByAccountNumber("ACC-340001")).thenReturn(false);
        when(cardAccountRepository.save(any(CardAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        cardAccountService.createCardAccount(createRequest());

        ArgumentCaptor<CardAccount> captor = ArgumentCaptor.forClass(CardAccount.class);
        verify(cardAccountRepository).save(captor.capture());
        return captor.getValue();
    }

    private CardAccount updateAccount(BigDecimal creditLimit, CardAccountStatus status) {
        CardAccount cardAccount = accountWithExposure();
        when(cardAccountRepository.findByAccountNumber("ACC-340002"))
                .thenReturn(Optional.of(cardAccount));
        when(cardAccountRepository.save(cardAccount)).thenReturn(cardAccount);

        cardAccountService.updateCardAccount(
                "ACC-340002",
                new CardAccountUpdateRequest(creditLimit, status)
        );

        ArgumentCaptor<CardAccount> captor = ArgumentCaptor.forClass(CardAccount.class);
        verify(cardAccountRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CardAccountCreateRequest createRequest() {
        return new CardAccountCreateRequest(
                "ACC-340001",
                new BigDecimal("10000.00"),
                "USD"
        );
    }

    private static CardAccount accountWithExposure() {
        return new CardAccount(
                "ACC-340002",
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("7000.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
    }
}
