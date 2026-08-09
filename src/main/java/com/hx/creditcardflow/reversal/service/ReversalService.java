package com.hx.creditcardflow.reversal.service;

import com.hx.creditcardflow.authorization.entity.Authorization;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.authorization.repository.AuthorizationRepository;
import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import com.hx.creditcardflow.reversal.dto.ReversalCreateRequest;
import com.hx.creditcardflow.reversal.dto.ReversalResponse;
import com.hx.creditcardflow.reversal.entity.AuthorizationReversal;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import com.hx.creditcardflow.reversal.exception.DuplicateReversalReferenceException;
import com.hx.creditcardflow.reversal.exception.IdempotencyKeyConflictException;
import com.hx.creditcardflow.reversal.exception.ReversalAmountMismatchException;
import com.hx.creditcardflow.reversal.exception.ReversalNotAllowedException;
import com.hx.creditcardflow.reversal.repository.AuthorizationReversalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReversalService {

    private final AuthorizationReversalRepository reversalRepository;
    private final AuthorizationRepository authorizationRepository;

    public ReversalService(
            AuthorizationReversalRepository reversalRepository,
            AuthorizationRepository authorizationRepository
    ) {
        this.reversalRepository = reversalRepository;
        this.authorizationRepository = authorizationRepository;
    }

    @Transactional
    public ReversalResponse createReversal(String idempotencyKey, ReversalCreateRequest request) {
        validateIdempotencyKey(idempotencyKey);

        AuthorizationReversal existing = reversalRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!matches(existing, request)) {
                throw new IdempotencyKeyConflictException(idempotencyKey);
            }
            return toResponse(existing);
        }

        if (reversalRepository.findByReversalReference(request.reversalReference()).isPresent()) {
            throw new DuplicateReversalReferenceException(request.reversalReference());
        }

        Authorization authorization = authorizationRepository
                .findByAuthorizationReference(request.authorizationReference())
                .orElseThrow(() -> new AuthorizationNotFoundException(request.authorizationReference()));

        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            throw new ReversalNotAllowedException(
                    authorization.getAuthorizationReference(),
                    authorization.getStatus()
            );
        }

        if (request.amount().compareTo(authorization.getAmount()) != 0) {
            throw new ReversalAmountMismatchException(request.amount(), authorization.getAmount());
        }

        CardAccount cardAccount = authorization.getCard().getCardAccount();
        cardAccount.releaseCredit(request.amount());
        authorization.markReversed();
        authorizationRepository.save(authorization);

        AuthorizationReversal reversal = new AuthorizationReversal(
                request.reversalReference(),
                idempotencyKey,
                authorization,
                request.amount(),
                ReversalStatus.COMPLETED
        );

        return toResponse(reversalRepository.save(reversal));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency key must be present and not exceed 100 characters");
        }
    }

    private boolean matches(AuthorizationReversal reversal, ReversalCreateRequest request) {
        return reversal.getReversalReference().equals(request.reversalReference())
                && reversal.getAuthorization().getAuthorizationReference()
                .equals(request.authorizationReference())
                && reversal.getAmount().compareTo(request.amount()) == 0;
    }

    private ReversalResponse toResponse(AuthorizationReversal reversal) {
        return new ReversalResponse(
                reversal.getId(),
                reversal.getReversalReference(),
                reversal.getAuthorization().getAuthorizationReference(),
                reversal.getAmount(),
                reversal.getStatus(),
                reversal.getCreatedAt(),
                reversal.getUpdatedAt()
        );
    }
}
