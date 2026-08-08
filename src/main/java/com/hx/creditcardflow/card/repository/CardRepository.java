package com.hx.creditcardflow.card.repository;

import com.hx.creditcardflow.card.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByCardReference(String cardReference);

    boolean existsByCardReference(String cardReference);
}
