package com.hx.creditcardflow.authorization.entity;

import com.hx.creditcardflow.card.entity.Card;
import com.hx.creditcardflow.merchant.entity.Merchant;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "authorizations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_authorizations_authorization_reference",
                columnNames = "authorization_reference"
        )
)
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_reference", nullable = false, length = 50)
    private String authorizationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_type", nullable = false)
    private AuthorizationType authorizationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthorizationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthorizationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Authorization() {
    }

    public Authorization(
            String authorizationReference,
            Card card,
            Merchant merchant,
            BigDecimal amount,
            String currencyCode,
            AuthorizationType authorizationType,
            AuthorizationChannel channel,
            AuthorizationStatus status
    ) {
        this.authorizationReference = authorizationReference;
        this.card = card;
        this.merchant = merchant;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.authorizationType = authorizationType;
        this.channel = channel;
        this.status = status;
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

    public void markReversed() {
        status = AuthorizationStatus.REVERSED;
    }

    public void markCleared() {
        status = AuthorizationStatus.CLEARED;
    }

    public Long getId() {
        return id;
    }

    public String getAuthorizationReference() {
        return authorizationReference;
    }

    public Card getCard() {
        return card;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public AuthorizationType getAuthorizationType() {
        return authorizationType;
    }

    public AuthorizationChannel getChannel() {
        return channel;
    }

    public AuthorizationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
