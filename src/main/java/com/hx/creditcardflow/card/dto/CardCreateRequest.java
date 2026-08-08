package com.hx.creditcardflow.card.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CardCreateRequest(
        @NotBlank(message = "Card reference must not be blank")
        @Size(max = 30, message = "Card reference must not exceed 30 characters")
        String cardReference,

        @NotBlank(message = "Last four must not be blank")
        @Pattern(regexp = "^\\d{4}$", message = "Last four must contain exactly four digits")
        String lastFour,

        @NotNull(message = "Expiration month must not be null")
        @Min(value = 1, message = "Expiration month must be at least 1")
        @Max(value = 12, message = "Expiration month must not exceed 12")
        Integer expirationMonth,

        @NotNull(message = "Expiration year must not be null")
        @Min(value = 2000, message = "Expiration year must be at least 2000")
        @Max(value = 9999, message = "Expiration year must not exceed 9999")
        Integer expirationYear,

        @NotBlank(message = "Card account number must not be blank")
        @Size(max = 30, message = "Card account number must not exceed 30 characters")
        String cardAccountNumber
) {
}
