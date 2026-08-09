package com.hx.creditcardflow.reversal.entity;

import com.hx.creditcardflow.authorization.entity.Authorization;
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
        name = "authorization_reversals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_authorization_reversals_reversal_reference",
                        columnNames = "reversal_reference"
                ),
                @UniqueConstraint(
                        name = "uk_authorization_reversals_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
public class AuthorizationReversal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reversal_reference", nullable = false, length = 50)
    private String reversalReference;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "authorization_id", nullable = false)
    private Authorization authorization;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReversalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthorizationReversal() {
    }

    public AuthorizationReversal(
            String reversalReference,
            String idempotencyKey,
            Authorization authorization,
            BigDecimal amount,
            ReversalStatus status
    ) {
        this.reversalReference = reversalReference;
        this.idempotencyKey = idempotencyKey;
        this.authorization = authorization;
        this.amount = amount;
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

    public Long getId() {
        return id;
    }

    public String getReversalReference() {
        return reversalReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ReversalStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
