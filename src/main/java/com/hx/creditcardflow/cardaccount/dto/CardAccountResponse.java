package com.hx.creditcardflow.cardaccount.dto;

import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CardAccountResponse(
        Long id,
        String accountNumber,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        BigDecimal availableCredit,
        String currencyCode,
        CardAccountStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
