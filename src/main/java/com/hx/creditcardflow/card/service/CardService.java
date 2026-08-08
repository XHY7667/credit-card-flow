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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;

@Service
@Transactional(readOnly = true)
public class CardService {

    private final CardRepository cardRepository;
    private final CardAccountRepository cardAccountRepository;

    public CardService(
            CardRepository cardRepository,
            CardAccountRepository cardAccountRepository
    ) {
        this.cardRepository = cardRepository;
        this.cardAccountRepository = cardAccountRepository;
    }

    @Transactional
    public CardResponse createCard(CardCreateRequest request) {
        if (cardRepository.existsByCardReference(request.cardReference())) {
            throw new DuplicateCardReferenceException(request.cardReference());
        }

        CardAccount cardAccount = cardAccountRepository
                .findByAccountNumber(request.cardAccountNumber())
                .orElseThrow(() -> new CardAccountNotFoundException(request.cardAccountNumber()));

        if (cardAccount.getStatus() != CardAccountStatus.ACTIVE) {
            throw new CardAccountNotEligibleForCardIssuanceException(
                    cardAccount.getAccountNumber(),
                    cardAccount.getStatus()
            );
        }

        YearMonth expiration = YearMonth.of(request.expirationYear(), request.expirationMonth());
        if (expiration.isBefore(YearMonth.now(ZoneOffset.UTC))) {
            throw new InvalidCardExpirationException(expiration);
        }

        Card card = new Card(
                request.cardReference(),
                request.lastFour(),
                request.expirationMonth(),
                request.expirationYear(),
                CardStatus.ACTIVE,
                cardAccount
        );

        return toResponse(cardRepository.save(card));
    }

    public CardResponse getCard(String cardReference) {
        return toResponse(findByCardReference(cardReference));
    }

    @Transactional
    public CardResponse updateCard(String cardReference, CardUpdateRequest request) {
        Card card = findByCardReference(cardReference);
        card.changeStatus(request.status());
        return toResponse(cardRepository.save(card));
    }

    private Card findByCardReference(String cardReference) {
        return cardRepository.findByCardReference(cardReference)
                .orElseThrow(() -> new CardNotFoundException(cardReference));
    }

    private CardResponse toResponse(Card card) {
        return new CardResponse(
                card.getId(),
                card.getCardReference(),
                card.getLastFour(),
                card.getExpirationMonth(),
                card.getExpirationYear(),
                card.getStatus(),
                card.getCardAccount().getAccountNumber(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
