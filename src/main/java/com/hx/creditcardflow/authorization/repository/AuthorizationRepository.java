package com.hx.creditcardflow.authorization.repository;

import com.hx.creditcardflow.authorization.entity.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {

    Optional<Authorization> findByAuthorizationReference(String authorizationReference);
}
