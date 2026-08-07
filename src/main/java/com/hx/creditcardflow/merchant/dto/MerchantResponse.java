package com.hx.creditcardflow.merchant.dto;

import com.hx.creditcardflow.merchant.entity.MerchantStatus;

import java.time.Instant;

public record MerchantResponse(
        Long id,
        String merchantCode,
        String legalName,
        String displayName,
        String merchantCategoryCode,
        String countryCode,
        MerchantStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
