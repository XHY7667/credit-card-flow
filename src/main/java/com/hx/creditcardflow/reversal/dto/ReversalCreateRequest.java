package com.hx.creditcardflow.reversal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReversalCreateRequest(
        @NotBlank(message = "Reversal reference must not be blank")
        @Size(max = 50, message = "Reversal reference must not exceed 50 characters")
        String reversalReference,

        @NotBlank(message = "Authorization reference must not be blank")
        @Size(max = 50, message = "Authorization reference must not exceed 50 characters")
        String authorizationReference,

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 fractional digits")
        BigDecimal amount
) {
}
