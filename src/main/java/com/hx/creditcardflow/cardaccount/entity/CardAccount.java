package com.hx.creditcardflow.cardaccount.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "card_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_card_accounts_account_number",
                columnNames = "account_number"
        )
)
public class CardAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "available_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableCredit;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardAccountStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardAccount() {
    }

    public CardAccount(
            String accountNumber,
            BigDecimal creditLimit,
            BigDecimal currentBalance,
            BigDecimal availableCredit,
            String currencyCode,
            CardAccountStatus status
    ) {
        this.accountNumber = accountNumber;
        this.creditLimit = creditLimit;
        this.currentBalance = currentBalance;
        this.availableCredit = availableCredit;
        this.currencyCode = currencyCode;
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

    public void update(
            BigDecimal creditLimit,
            BigDecimal availableCredit,
            CardAccountStatus status
    ) {
        this.creditLimit = creditLimit;
        this.availableCredit = availableCredit;
        this.status = status;
    }

    public boolean reserveCredit(BigDecimal amount) {
        if (availableCredit.compareTo(amount) < 0) {
            return false;
        }

        availableCredit = availableCredit.subtract(amount);
        return true;
    }

    public void releaseCredit(BigDecimal amount) {
        BigDecimal committedExposure = creditLimit.subtract(availableCredit);
        if (amount.compareTo(committedExposure) > 0) {
            throw new IllegalArgumentException("Release amount exceeds committed credit exposure");
        }

        availableCredit = availableCredit.add(amount);
    }

    public void postClearing(BigDecimal amount) {
        BigDecimal pendingAuthorizationExposure = creditLimit
                .subtract(availableCredit)
                .subtract(currentBalance);
        if (amount.compareTo(pendingAuthorizationExposure) > 0) {
            throw new IllegalArgumentException(
                    "Clearing amount exceeds pending authorization exposure"
            );
        }

        currentBalance = currentBalance.add(amount);
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getAvailableCredit() {
        return availableCredit;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public CardAccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
