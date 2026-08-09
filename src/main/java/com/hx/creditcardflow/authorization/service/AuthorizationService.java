package com.hx.creditcardflow.authorization.service;

import com.hx.creditcardflow.authorization.dto.AuthorizationCreateRequest;
import com.hx.creditcardflow.authorization.dto.AuthorizationResponse;
import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.exception.DuplicateAuthorizationReferenceException;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;

@Service
@Transactional(readOnly = true)
public class AuthorizationService {

    private final AuthorizationRepository authorizationRepository;
    private final CardRepository cardRepository;
    private final MerchantRepository merchantRepository;

    public AuthorizationService(
            AuthorizationRepository authorizationRepository,
            CardRepository cardRepository,
            MerchantRepository merchantRepository
    ) {
        this.authorizationRepository = authorizationRepository;
        this.cardRepository = cardRepository;
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public AuthorizationResponse createAuthorization(AuthorizationCreateRequest request) {
        if (authorizationRepository.findByAuthorizationReference(request.authorizationReference()).isPresent()) {
            throw new DuplicateAuthorizationReferenceException(request.authorizationReference());
        }

        Card card = cardRepository.findByCardReference(request.cardReference())
                .orElseThrow(() -> new CardNotFoundException(request.cardReference()));
        Merchant merchant = merchantRepository.findByMerchantCode(request.merchantCode())
                .orElseThrow(() -> new MerchantNotFoundException(request.merchantCode()));
        CardAccount cardAccount = card.getCardAccount();

        boolean otherwiseEligible = card.getStatus() == CardStatus.ACTIVE
                && !isExpired(card)
                && cardAccount.getStatus() == CardAccountStatus.ACTIVE
                && merchant.getStatus() == MerchantStatus.ACTIVE;
        boolean approved = otherwiseEligible && cardAccount.reserveCredit(request.amount());

        Authorization authorization = new Authorization(
                request.authorizationReference(),
                card,
                merchant,
                request.amount(),
                request.currencyCode(),
                request.authorizationType(),
                request.channel(),
                approved ? AuthorizationStatus.APPROVED : AuthorizationStatus.DECLINED
        );

        return toResponse(authorizationRepository.save(authorization));
    }

    public AuthorizationResponse getAuthorization(String authorizationReference) {
        Authorization authorization = authorizationRepository
                .findByAuthorizationReference(authorizationReference)
                .orElseThrow(() -> new AuthorizationNotFoundException(authorizationReference));
        return toResponse(authorization);
    }

    private boolean isExpired(Card card) {
        YearMonth expiration = YearMonth.of(card.getExpirationYear(), card.getExpirationMonth());
        return expiration.isBefore(YearMonth.now(ZoneOffset.UTC));
    }

    private AuthorizationResponse toResponse(Authorization authorization) {
        return new AuthorizationResponse(
                authorization.getId(),
                authorization.getAuthorizationReference(),
                authorization.getCard().getCardReference(),
                authorization.getMerchant().getMerchantCode(),
                authorization.getAmount(),
                authorization.getCurrencyCode(),
                authorization.getAuthorizationType(),
                authorization.getChannel(),
                authorization.getStatus(),
                authorization.getCreatedAt(),
                authorization.getUpdatedAt()
        );
    }
}
