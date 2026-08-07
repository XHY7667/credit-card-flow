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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class CardAccountService {

    private final CardAccountRepository cardAccountRepository;

    public CardAccountService(CardAccountRepository cardAccountRepository) {
        this.cardAccountRepository = cardAccountRepository;
    }

    @Transactional
    public CardAccountResponse createCardAccount(CardAccountCreateRequest request) {
        if (cardAccountRepository.existsByAccountNumber(request.accountNumber())) {
            throw new DuplicateCardAccountNumberException(request.accountNumber());
        }

        CardAccount cardAccount = new CardAccount(
                request.accountNumber(),
                request.creditLimit(),
                BigDecimal.ZERO,
                request.creditLimit(),
                request.currencyCode(),
                CardAccountStatus.ACTIVE
        );

        return toResponse(cardAccountRepository.save(cardAccount));
    }

    public CardAccountResponse getCardAccount(String accountNumber) {
        return toResponse(findByAccountNumber(accountNumber));
    }

    @Transactional
    public CardAccountResponse updateCardAccount(String accountNumber, CardAccountUpdateRequest request) {
        CardAccount cardAccount = findByAccountNumber(accountNumber);
        BigDecimal committedExposure = cardAccount.getCreditLimit()
                .subtract(cardAccount.getAvailableCredit());

        if (request.creditLimit().compareTo(committedExposure) < 0) {
            throw new InvalidCardAccountCreditLimitException(request.creditLimit(), committedExposure);
        }

        BigDecimal availableCredit = request.creditLimit().subtract(committedExposure);
        cardAccount.update(request.creditLimit(), availableCredit, request.status());

        return toResponse(cardAccountRepository.save(cardAccount));
    }

    private CardAccount findByAccountNumber(String accountNumber) {
        return cardAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new CardAccountNotFoundException(accountNumber));
    }

    private CardAccountResponse toResponse(CardAccount cardAccount) {
        return new CardAccountResponse(
                cardAccount.getId(),
                cardAccount.getAccountNumber(),
                cardAccount.getCreditLimit(),
                cardAccount.getCurrentBalance(),
                cardAccount.getAvailableCredit(),
                cardAccount.getCurrencyCode(),
                cardAccount.getStatus(),
                cardAccount.getVersion(),
                cardAccount.getCreatedAt(),
                cardAccount.getUpdatedAt()
        );
    }
}
