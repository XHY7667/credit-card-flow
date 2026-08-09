package com.hx.creditcardflow.clearing.repository;

import com.hx.creditcardflow.clearing.entity.Clearing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClearingRepository extends JpaRepository<Clearing, Long> {

    Optional<Clearing> findByClearingReference(String clearingReference);
}
