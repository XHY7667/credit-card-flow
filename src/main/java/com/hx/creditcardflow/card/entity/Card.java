package com.hx.creditcardflow.card.entity;

import com.hx.creditcardflow.cardaccount.entity.CardAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "cards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cards_card_reference",
                columnNames = "card_reference"
        )
)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_reference", nullable = false, length = 30)
    private String cardReference;

    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    @Column(name = "expiration_month", nullable = false)
    private Integer expirationMonth;

    @Column(name = "expiration_year", nullable = false)
    private Integer expirationYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_account_id", nullable = false)
    private CardAccount cardAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Card() {
    }

    public Card(
            String cardReference,
            String lastFour,
            Integer expirationMonth,
            Integer expirationYear,
            CardStatus status,
            CardAccount cardAccount
    ) {
        this.cardReference = cardReference;
        this.lastFour = lastFour;
        this.expirationMonth = expirationMonth;
        this.expirationYear = expirationYear;
        this.status = status;
        this.cardAccount = cardAccount;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changeStatus(CardStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getCardReference() {
        return cardReference;
    }

    public String getLastFour() {
        return lastFour;
    }

    public Integer getExpirationMonth() {
        return expirationMonth;
    }

    public Integer getExpirationYear() {
        return expirationYear;
    }

    public CardStatus getStatus() {
        return status;
    }

    public CardAccount getCardAccount() {
        return cardAccount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
