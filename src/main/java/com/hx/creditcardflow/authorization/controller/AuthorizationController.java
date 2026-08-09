package com.hx.creditcardflow.authorization.controller;

import com.hx.creditcardflow.authorization.dto.AuthorizationCreateRequest;
import com.hx.creditcardflow.authorization.dto.AuthorizationResponse;
import com.hx.creditcardflow.authorization.service.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authorizations")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ResponseEntity<AuthorizationResponse> createAuthorization(
            @Valid @RequestBody AuthorizationCreateRequest request
    ) {
        AuthorizationResponse response = authorizationService.createAuthorization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{authorizationReference}")
    public ResponseEntity<AuthorizationResponse> getAuthorization(
            @PathVariable String authorizationReference
    ) {
        return ResponseEntity.ok(authorizationService.getAuthorization(authorizationReference));
    }
}
