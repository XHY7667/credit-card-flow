package com.hx.creditcardflow.clearing.service;

import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.authorization.repository.AuthorizationRepository;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.clearing.dto.ClearingCreateRequest;
import com.hx.creditcardflow.clearing.dto.ClearingResponse;
import com.hx.creditcardflow.clearing.entity.Clearing;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.event.ClearingPostedEvent;
import com.hx.creditcardflow.clearing.exception.ClearingAmountMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingCurrencyMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingNotAllowedException;
import com.hx.creditcardflow.clearing.exception.ClearingNotFoundException;
import com.hx.creditcardflow.clearing.exception.DuplicateClearingReferenceException;
import com.hx.creditcardflow.clearing.repository.ClearingRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClearingService {

    private final ClearingRepository clearingRepository;
    private final AuthorizationRepository authorizationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ClearingService(
            ClearingRepository clearingRepository,
            AuthorizationRepository authorizationRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.clearingRepository = clearingRepository;
        this.authorizationRepository = authorizationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ClearingResponse createClearing(ClearingCreateRequest request) {
        if (clearingRepository.findByClearingReference(request.clearingReference()).isPresent()) {
            throw new DuplicateClearingReferenceException(request.clearingReference());
        }

        Authorization authorization = authorizationRepository
                .findByAuthorizationReference(request.authorizationReference())
                .orElseThrow(() -> new AuthorizationNotFoundException(
                        request.authorizationReference()
                ));

        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            throw new ClearingNotAllowedException(
                    authorization.getAuthorizationReference(),
                    authorization.getStatus()
            );
        }

        if (request.amount().compareTo(authorization.getAmount()) != 0) {
            throw new ClearingAmountMismatchException(
                    request.amount(), authorization.getAmount()
            );
        }

        if (!request.currencyCode().equals(authorization.getCurrencyCode())) {
            throw new ClearingCurrencyMismatchException(
                    request.currencyCode(), authorization.getCurrencyCode()
            );
        }

        CardAccount cardAccount = authorization.getCard().getCardAccount();
        cardAccount.postClearing(request.amount());
        authorization.markCleared();
        authorizationRepository.save(authorization);

        Clearing clearing = new Clearing(
                request.clearingReference(),
                authorization,
                request.amount(),
                request.currencyCode(),
                ClearingStatus.POSTED
        );

        Clearing savedClearing = clearingRepository.save(clearing);
        eventPublisher.publishEvent(new ClearingPostedEvent(
                UUID.randomUUID(),
                savedClearing.getClearingReference(),
                savedClearing.getAuthorization().getAuthorizationReference(),
                savedClearing.getAmount(),
                savedClearing.getCurrencyCode(),
                savedClearing.getStatus(),
                Instant.now()
        ));

        return toResponse(savedClearing);
    }

    public ClearingResponse getClearing(String clearingReference) {
        return clearingRepository.findByClearingReference(clearingReference)
                .map(this::toResponse)
                .orElseThrow(() -> new ClearingNotFoundException(clearingReference));
    }

    private ClearingResponse toResponse(Clearing clearing) {
        return new ClearingResponse(
                clearing.getId(),
                clearing.getClearingReference(),
                clearing.getAuthorization().getAuthorizationReference(),
                clearing.getAmount(),
                clearing.getCurrencyCode(),
                clearing.getStatus(),
                clearing.getCreatedAt(),
                clearing.getUpdatedAt()
        );
    }
}
