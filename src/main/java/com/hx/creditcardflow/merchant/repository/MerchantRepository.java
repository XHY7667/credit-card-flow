package com.hx.creditcardflow.merchant.repository;

import com.hx.creditcardflow.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByMerchantCode(String merchantCode);

    boolean existsByMerchantCode(String merchantCode);
}
