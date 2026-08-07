package com.hx.creditcardflow.merchant.service;

import com.hx.creditcardflow.merchant.dto.MerchantCreateRequest;
import com.hx.creditcardflow.merchant.dto.MerchantResponse;
import com.hx.creditcardflow.merchant.dto.MerchantUpdateRequest;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.exception.DuplicateMerchantCodeException;
import com.hx.creditcardflow.merchant.exception.MerchantNotFoundException;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private MerchantService merchantService;

    @Test
    void shouldCreateMerchant() {
        MerchantCreateRequest request = new MerchantCreateRequest(
                "M200001",
                "Silver Pine Market LLC",
                "Silver Pine Market",
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );
        when(merchantRepository.existsByMerchantCode("M200001")).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantResponse response = merchantService.createMerchant(request);

        assertThat(response.merchantCode()).isEqualTo("M200001");
        assertThat(response.legalName()).isEqualTo("Silver Pine Market LLC");
        assertThat(response.displayName()).isEqualTo("Silver Pine Market");
        assertThat(response.merchantCategoryCode()).isEqualTo("5411");
        assertThat(response.countryCode()).isEqualTo("US");
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);
        verify(merchantRepository).existsByMerchantCode("M200001");
        verify(merchantRepository).save(any(Merchant.class));
    }

    @Test
    void shouldRejectDuplicateMerchantCode() {
        MerchantCreateRequest request = new MerchantCreateRequest(
                "M200002",
                "Willow Creek Cafe LLC",
                "Willow Creek Cafe",
                "5812",
                "US",
                MerchantStatus.ACTIVE
        );
        when(merchantRepository.existsByMerchantCode("M200002")).thenReturn(true);

        assertThatThrownBy(() -> merchantService.createMerchant(request))
                .isInstanceOf(DuplicateMerchantCodeException.class)
                .hasMessage("Merchant code already exists: M200002");
        verify(merchantRepository).existsByMerchantCode("M200002");
        verify(merchantRepository, never()).save(any(Merchant.class));
    }

    @Test
    void shouldGetMerchantById() {
        Merchant merchant = merchant(
                "M200003",
                "Redwood Office Supply LLC",
                "Redwood Office Supply",
                "5943",
                "US",
                MerchantStatus.ACTIVE
        );
        when(merchantRepository.findById(3L)).thenReturn(Optional.of(merchant));

        MerchantResponse response = merchantService.getMerchant(3L);

        assertThat(response.merchantCode()).isEqualTo("M200003");
        assertThat(response.legalName()).isEqualTo("Redwood Office Supply LLC");
        assertThat(response.displayName()).isEqualTo("Redwood Office Supply");
        assertThat(response.merchantCategoryCode()).isEqualTo("5943");
        assertThat(response.countryCode()).isEqualTo("US");
        assertThat(response.status()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    void shouldThrowWhenMerchantDoesNotExist() {
        when(merchantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getMerchant(404L))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessage("Merchant not found with id: 404");
    }

    @Test
    void shouldReturnAllMerchants() {
        Merchant firstMerchant = merchant(
                "M200004",
                "Harbor Light Books LLC",
                "Harbor Light Books",
                "5942",
                "US",
                MerchantStatus.ACTIVE
        );
        Merchant secondMerchant = merchant(
                "M200005",
                "Canyon Cycle Works LLC",
                "Canyon Cycle Works",
                "5940",
                "US",
                MerchantStatus.SUSPENDED
        );
        when(merchantRepository.findAll()).thenReturn(List.of(firstMerchant, secondMerchant));

        List<MerchantResponse> responses = merchantService.getAllMerchants();

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(MerchantResponse::merchantCode)
                .containsExactly("M200004", "M200005");
    }

    @Test
    void shouldUpdateMerchantWithoutChangingMerchantCode() {
        Merchant merchant = merchant(
                "M200006",
                "Prairie Home Goods LLC",
                "Prairie Home Goods",
                "5712",
                "US",
                MerchantStatus.ACTIVE
        );
        String originalMerchantCode = merchant.getMerchantCode();
        MerchantUpdateRequest request = new MerchantUpdateRequest(
                "Prairie Home Furnishings LLC",
                "Prairie Home Furnishings",
                "5719",
                "CA",
                MerchantStatus.SUSPENDED
        );
        when(merchantRepository.findById(6L)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(merchant)).thenReturn(merchant);

        MerchantResponse response = merchantService.updateMerchant(6L, request);

        assertThat(response.legalName()).isEqualTo("Prairie Home Furnishings LLC");
        assertThat(response.displayName()).isEqualTo("Prairie Home Furnishings");
        assertThat(response.merchantCategoryCode()).isEqualTo("5719");
        assertThat(response.countryCode()).isEqualTo("CA");
        assertThat(response.status()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(response.merchantCode()).isEqualTo(originalMerchantCode);
        assertThat(merchant.getMerchantCode()).isEqualTo(originalMerchantCode);
        verify(merchantRepository).save(merchant);
    }

    @Test
    void shouldDeleteExistingMerchant() {
        Merchant merchant = merchant(
                "M200007",
                "Elm Street Florist LLC",
                "Elm Street Florist",
                "5992",
                "US",
                MerchantStatus.ACTIVE
        );
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));

        merchantService.deleteMerchant(7L);

        verify(merchantRepository).delete(merchant);
    }

    @Test
    void shouldRejectDeletingMissingMerchant() {
        when(merchantRepository.findById(808L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.deleteMerchant(808L))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessage("Merchant not found with id: 808");
        verify(merchantRepository, never()).delete(any(Merchant.class));
    }

    private static Merchant merchant(
            String merchantCode,
            String legalName,
            String displayName,
            String merchantCategoryCode,
            String countryCode,
            MerchantStatus status
    ) {
        return new Merchant(
                merchantCode,
                legalName,
                displayName,
                merchantCategoryCode,
                countryCode,
                status
        );
    }
}
