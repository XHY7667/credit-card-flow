package com.hx.creditcardflow.security.authentication.service;

import com.hx.creditcardflow.security.authentication.dto.LoginRequest;
import com.hx.creditcardflow.security.authentication.dto.LoginResponse;
import com.hx.creditcardflow.security.config.SecurityConfig;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthenticationService {

    public static final long ACCESS_TOKEN_LIFETIME_SECONDS = 1800;
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(), request.password()
                )
        );

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ACCESS_TOKEN_LIFETIME_SECONDS);
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user does not have an application role"
                ));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER)
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
        return new LoginResponse(accessToken, "Bearer", ACCESS_TOKEN_LIFETIME_SECONDS);
    }
}
