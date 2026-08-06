package com.hx.creditcardflow.merchant.repository;

import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class MerchantRepositoryTest {

    @Autowired
    private MerchantRepository merchantRepository;

    @Test
    void shouldSaveAndFindMerchantByCode() {
        Merchant merchant = new Merchant(
                "M100001",
                "Northstar Market Group LLC",
                "Northstar Market",
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );

        Merchant savedMerchant = merchantRepository.saveAndFlush(merchant);

        assertThat(savedMerchant.getId()).isNotNull();
        assertThat(savedMerchant.getCreatedAt()).isNotNull();
        assertThat(savedMerchant.getUpdatedAt()).isNotNull();

        Optional<Merchant> result = merchantRepository.findByMerchantCode("M100001");

        assertThat(result).isPresent();
        assertThat(result.get().getLegalName()).isEqualTo("Northstar Market Group LLC");
        assertThat(result.get().getDisplayName()).isEqualTo("Northstar Market");
        assertThat(result.get().getMerchantCategoryCode()).isEqualTo("5411");
        assertThat(result.get().getCountryCode()).isEqualTo("US");
        assertThat(result.get().getStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    void shouldReturnTrueWhenMerchantCodeExists() {
        Merchant merchant = new Merchant(
                "M100002",
                "Blue Harbor Books LLC",
                "Blue Harbor Books",
                "5942",
                "US",
                MerchantStatus.ACTIVE
        );
        merchantRepository.saveAndFlush(merchant);

        assertThat(merchantRepository.existsByMerchantCode("M100002")).isTrue();
        assertThat(merchantRepository.existsByMerchantCode("M999999")).isFalse();
    }

    @Test
    void shouldRejectDuplicateMerchantCode() {
        Merchant firstMerchant = new Merchant(
                "M100003",
                "Cedar Trail Outfitters LLC",
                "Cedar Trail Outfitters",
                "5941",
                "US",
                MerchantStatus.ACTIVE
        );
        merchantRepository.saveAndFlush(firstMerchant);

        Merchant duplicateMerchant = new Merchant(
                "M100003",
                "Summit Ridge Supply LLC",
                "Summit Ridge Supply",
                "5941",
                "US",
                MerchantStatus.ACTIVE
        );

        assertThatThrownBy(() -> merchantRepository.saveAndFlush(duplicateMerchant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
