package com.hx.creditcardflow.card.dto;

import com.hx.creditcardflow.card.entity.CardStatus;
import jakarta.validation.constraints.NotNull;

public record CardUpdateRequest(
        @NotNull(message = "Card status must not be null")
        CardStatus status
) {
}
