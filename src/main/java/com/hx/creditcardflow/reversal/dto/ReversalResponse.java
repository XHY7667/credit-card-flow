package com.hx.creditcardflow.reversal.dto;

import com.hx.creditcardflow.reversal.entity.ReversalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ReversalResponse(
        Long id,
        String reversalReference,
        String authorizationReference,
        BigDecimal amount,
        ReversalStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
