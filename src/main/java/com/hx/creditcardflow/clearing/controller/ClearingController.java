package com.hx.creditcardflow.clearing.controller;

import com.hx.creditcardflow.clearing.dto.ClearingCreateRequest;
import com.hx.creditcardflow.clearing.dto.ClearingResponse;
import com.hx.creditcardflow.clearing.service.ClearingService;
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
@RequestMapping("/api/v1/clearings")
public class ClearingController {

    private final ClearingService clearingService;

    public ClearingController(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    @PostMapping
    public ResponseEntity<ClearingResponse> createClearing(
            @Valid @RequestBody ClearingCreateRequest request
    ) {
        ClearingResponse response = clearingService.createClearing(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{clearingReference}")
    public ResponseEntity<ClearingResponse> getClearing(@PathVariable String clearingReference) {
        return ResponseEntity.ok(clearingService.getClearing(clearingReference));
    }
}
