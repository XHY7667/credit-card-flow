package com.hx.creditcardflow.cardaccount.dto;

import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CardAccountUpdateRequest(
        @NotNull(message = "Credit limit must not be null")
        @DecimalMin(value = "0.01", message = "Credit limit must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Credit limit must have at most 17 integer digits and 2 fractional digits")
        BigDecimal creditLimit,

        @NotNull(message = "Card account status must not be null")
        CardAccountStatus status
) {
}
