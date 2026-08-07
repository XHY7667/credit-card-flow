package com.hx.creditcardflow.cardaccount.controller;

import com.hx.creditcardflow.cardaccount.dto.CardAccountCreateRequest;
import com.hx.creditcardflow.cardaccount.dto.CardAccountResponse;
import com.hx.creditcardflow.cardaccount.dto.CardAccountUpdateRequest;
import com.hx.creditcardflow.cardaccount.service.CardAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-accounts")
public class CardAccountController {

    private final CardAccountService cardAccountService;

    public CardAccountController(CardAccountService cardAccountService) {
        this.cardAccountService = cardAccountService;
    }

    @PostMapping
    public ResponseEntity<CardAccountResponse> createCardAccount(
            @Valid @RequestBody CardAccountCreateRequest request
    ) {
        CardAccountResponse response = cardAccountService.createCardAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<CardAccountResponse> getCardAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(cardAccountService.getCardAccount(accountNumber));
    }

    @PutMapping("/{accountNumber}")
    public ResponseEntity<CardAccountResponse> updateCardAccount(
            @PathVariable String accountNumber,
            @Valid @RequestBody CardAccountUpdateRequest request
    ) {
        return ResponseEntity.ok(cardAccountService.updateCardAccount(accountNumber, request));
    }
}
