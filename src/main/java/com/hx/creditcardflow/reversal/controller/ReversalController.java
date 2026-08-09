package com.hx.creditcardflow.reversal.controller;

import com.hx.creditcardflow.reversal.dto.ReversalCreateRequest;
import com.hx.creditcardflow.reversal.dto.ReversalResponse;
import com.hx.creditcardflow.reversal.service.ReversalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reversals")
public class ReversalController {

    private final ReversalService reversalService;

    public ReversalController(ReversalService reversalService) {
        this.reversalService = reversalService;
    }

    @PostMapping
    public ResponseEntity<ReversalResponse> createReversal(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReversalCreateRequest request
    ) {
        ReversalResponse response = reversalService.createReversal(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{reversalReference}")
    public ResponseEntity<ReversalResponse> getReversal(@PathVariable String reversalReference) {
        return ResponseEntity.ok(reversalService.getReversal(reversalReference));
    }
}
