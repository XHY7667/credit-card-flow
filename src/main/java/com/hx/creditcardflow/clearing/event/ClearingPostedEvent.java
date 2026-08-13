package com.hx.creditcardflow.clearing.event;

import com.hx.creditcardflow.clearing.entity.ClearingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClearingPostedEvent(
        UUID eventId,
        String clearingReference,
        String authorizationReference,
        BigDecimal amount,
        String currency,
        ClearingStatus status,
        Instant occurredAt
) {
}
