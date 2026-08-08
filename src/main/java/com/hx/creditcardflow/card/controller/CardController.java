package com.hx.creditcardflow.card.controller;

import com.hx.creditcardflow.card.dto.CardCreateRequest;
import com.hx.creditcardflow.card.dto.CardResponse;
import com.hx.creditcardflow.card.dto.CardUpdateRequest;
import com.hx.creditcardflow.card.service.CardService;
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
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping
    public ResponseEntity<CardResponse> createCard(
            @Valid @RequestBody CardCreateRequest request
    ) {
        CardResponse response = cardService.createCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{cardReference}")
    public ResponseEntity<CardResponse> getCard(@PathVariable String cardReference) {
        return ResponseEntity.ok(cardService.getCard(cardReference));
    }

    @PutMapping("/{cardReference}")
    public ResponseEntity<CardResponse> updateCard(
            @PathVariable String cardReference,
            @Valid @RequestBody CardUpdateRequest request
    ) {
        return ResponseEntity.ok(cardService.updateCard(cardReference, request));
    }
}
