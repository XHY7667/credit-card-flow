package com.hx.creditcardflow.authorization.dto;

import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AuthorizationCreateRequest(
        @NotBlank(message = "Authorization reference must not be blank")
        @Size(max = 50, message = "Authorization reference must not exceed 50 characters")
        String authorizationReference,

        @NotBlank(message = "Card reference must not be blank")
        @Size(max = 30, message = "Card reference must not exceed 30 characters")
        String cardReference,

        @NotBlank(message = "Merchant code must not be blank")
        @Size(max = 20, message = "Merchant code must not exceed 20 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Merchant code must contain only uppercase letters, digits, underscores, or hyphens")
        String merchantCode,

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 fractional digits")
        BigDecimal amount,

        @NotBlank(message = "Currency code must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must contain exactly three uppercase letters")
        String currencyCode,

        @NotNull(message = "Authorization type must not be null")
        AuthorizationType authorizationType,

        @NotNull(message = "Authorization channel must not be null")
        AuthorizationChannel channel
) {
}
