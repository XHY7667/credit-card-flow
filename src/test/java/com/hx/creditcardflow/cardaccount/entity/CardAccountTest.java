package com.hx.creditcardflow.cardaccount.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CardAccountTest {

    @Test
    void reserveCreditSubtractsExactAmountWhenAvailable() {
        CardAccount cardAccount = accountWithAvailableCredit("7000.00");

        boolean reserved = cardAccount.reserveCredit(new BigDecimal("125.75"));

        assertThat(reserved).isTrue();
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("6874.25");
    }

    @Test
    void reserveCreditDoesNotMutateWhenAmountExceedsAvailableCredit() {
        CardAccount cardAccount = accountWithAvailableCredit("100.00");

        boolean reserved = cardAccount.reserveCredit(new BigDecimal("125.75"));

        assertThat(reserved).isFalse();
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("100.00");
    }

    private static CardAccount accountWithAvailableCredit(String availableCredit) {
        return new CardAccount(
                "ACC-430002",
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal(availableCredit),
                "USD",
                CardAccountStatus.ACTIVE
        );
    }
}
