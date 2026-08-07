package com.hx.creditcardflow.cardaccount.repository;

import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardAccountRepository extends JpaRepository<CardAccount, Long> {

    Optional<CardAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);
}
