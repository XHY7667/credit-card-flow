package com.hx.creditcardflow.security.authentication.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
