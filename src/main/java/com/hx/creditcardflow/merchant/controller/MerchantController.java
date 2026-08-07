package com.hx.creditcardflow.merchant.controller;

import com.hx.creditcardflow.merchant.dto.MerchantCreateRequest;
import com.hx.creditcardflow.merchant.dto.MerchantResponse;
import com.hx.creditcardflow.merchant.dto.MerchantUpdateRequest;
import com.hx.creditcardflow.merchant.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    public ResponseEntity<MerchantResponse> createMerchant(
            @Valid @RequestBody MerchantCreateRequest request
    ) {
        MerchantResponse response = merchantService.createMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MerchantResponse> getMerchant(@PathVariable Long id) {
        return ResponseEntity.ok(merchantService.getMerchant(id));
    }

    @GetMapping
    public ResponseEntity<List<MerchantResponse>> getAllMerchants() {
        return ResponseEntity.ok(merchantService.getAllMerchants());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MerchantResponse> updateMerchant(
            @PathVariable Long id,
            @Valid @RequestBody MerchantUpdateRequest request
    ) {
        return ResponseEntity.ok(merchantService.updateMerchant(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMerchant(@PathVariable Long id) {
        merchantService.deleteMerchant(id);
        return ResponseEntity.noContent().build();
    }
}
