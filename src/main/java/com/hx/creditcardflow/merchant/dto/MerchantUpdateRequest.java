package com.hx.creditcardflow.merchant.dto;

import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantUpdateRequest(
        @NotBlank(message = "Legal name must not be blank")
        @Size(max = 150, message = "Legal name must not exceed 150 characters")
        String legalName,

        @NotBlank(message = "Display name must not be blank")
        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName,

        @NotBlank(message = "Merchant category code must not be blank")
        @Pattern(regexp = "^\\d{4}$", message = "Merchant category code must contain exactly four digits")
        String merchantCategoryCode,

        @NotBlank(message = "Country code must not be blank")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must contain exactly two uppercase letters")
        String countryCode,

        @NotNull(message = "Merchant status must not be null")
        MerchantStatus status
) {
}
