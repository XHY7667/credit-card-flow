package com.hx.creditcardflow.merchant.service;

import com.hx.creditcardflow.merchant.dto.MerchantCreateRequest;
import com.hx.creditcardflow.merchant.dto.MerchantResponse;
import com.hx.creditcardflow.merchant.dto.MerchantUpdateRequest;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.exception.DuplicateMerchantCodeException;
import com.hx.creditcardflow.merchant.exception.MerchantNotFoundException;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Transactional
    public MerchantResponse createMerchant(MerchantCreateRequest request) {
        if (merchantRepository.existsByMerchantCode(request.merchantCode())) {
            throw new DuplicateMerchantCodeException(request.merchantCode());
        }

        Merchant merchant = new Merchant(
                request.merchantCode(),
                request.legalName(),
                request.displayName(),
                request.merchantCategoryCode(),
                request.countryCode(),
                request.status()
        );

        return toResponse(merchantRepository.save(merchant));
    }

    public MerchantResponse getMerchant(Long id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException(id));
        return toResponse(merchant);
    }

    public List<MerchantResponse> getAllMerchants() {
        return merchantRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MerchantResponse updateMerchant(Long id, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException(id));

        merchant.setLegalName(request.legalName());
        merchant.setDisplayName(request.displayName());
        merchant.setMerchantCategoryCode(request.merchantCategoryCode());
        merchant.setCountryCode(request.countryCode());
        merchant.setStatus(request.status());

        return toResponse(merchantRepository.save(merchant));
    }

    @Transactional
    public void deleteMerchant(Long id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException(id));
        merchantRepository.delete(merchant);
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getMerchantCode(),
                merchant.getLegalName(),
                merchant.getDisplayName(),
                merchant.getMerchantCategoryCode(),
                merchant.getCountryCode(),
                merchant.getStatus(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt()
        );
    }
}
