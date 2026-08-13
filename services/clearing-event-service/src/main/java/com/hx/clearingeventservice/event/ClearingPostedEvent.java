package com.hx.clearingeventservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClearingPostedEvent(
        UUID eventId,
        String clearingReference,
        String authorizationReference,
        BigDecimal amount,
        String currency,
        String status,
        Instant occurredAt
) {
}
