package com.hx.creditcardflow.cardaccount.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CardAccountCreateRequest(
        @NotBlank(message = "Account number must not be blank")
        @Size(max = 30, message = "Account number must not exceed 30 characters")
        String accountNumber,

        @NotNull(message = "Credit limit must not be null")
        @DecimalMin(value = "0.01", message = "Credit limit must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Credit limit must have at most 17 integer digits and 2 fractional digits")
        BigDecimal creditLimit,

        @NotBlank(message = "Currency code must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must contain exactly three uppercase letters")
        String currencyCode
) {
}
