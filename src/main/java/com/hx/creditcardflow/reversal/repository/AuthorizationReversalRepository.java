package com.hx.creditcardflow.reversal.repository;

import com.hx.creditcardflow.reversal.entity.AuthorizationReversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizationReversalRepository extends JpaRepository<AuthorizationReversal, Long> {

    Optional<AuthorizationReversal> findByReversalReference(String reversalReference);

    Optional<AuthorizationReversal> findByIdempotencyKey(String idempotencyKey);
}
