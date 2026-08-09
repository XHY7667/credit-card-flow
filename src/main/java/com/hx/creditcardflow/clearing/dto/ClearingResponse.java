package com.hx.creditcardflow.clearing.dto;

import com.hx.creditcardflow.clearing.entity.ClearingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ClearingResponse(
        Long id,
        String clearingReference,
        String authorizationReference,
        BigDecimal amount,
        String currencyCode,
        ClearingStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
