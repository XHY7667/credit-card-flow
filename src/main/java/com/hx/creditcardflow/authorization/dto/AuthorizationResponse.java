package com.hx.creditcardflow.authorization.dto;

import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;

import java.math.BigDecimal;
import java.time.Instant;

public record AuthorizationResponse(
        Long id,
        String authorizationReference,
        String cardReference,
        String merchantCode,
        BigDecimal amount,
        String currencyCode,
        AuthorizationType authorizationType,
        AuthorizationChannel channel,
        AuthorizationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
