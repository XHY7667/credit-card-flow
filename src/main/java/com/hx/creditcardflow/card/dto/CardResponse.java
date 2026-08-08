package com.hx.creditcardflow.card.dto;

import com.hx.creditcardflow.card.entity.CardStatus;

import java.time.Instant;

public record CardResponse(
        Long id,
        String cardReference,
        String lastFour,
        Integer expirationMonth,
        Integer expirationYear,
        CardStatus status,
        String cardAccountNumber,
        Instant createdAt,
        Instant updatedAt
) {
}
