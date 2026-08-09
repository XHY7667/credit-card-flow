package com.hx.creditcardflow.cardaccount.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void releaseCreditAddsExactAmount() {
        CardAccount cardAccount = accountWithAvailableCredit("6874.25");

        cardAccount.releaseCredit(new BigDecimal("125.75"));

        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    @Test
    void releaseCreditIsSymmetricWithReserveCredit() {
        CardAccount cardAccount = accountWithAvailableCredit("7000.00");

        cardAccount.reserveCredit(new BigDecimal("125.75"));
        cardAccount.releaseCredit(new BigDecimal("125.75"));

        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("7000.00");
    }

    @Test
    void releaseCreditDoesNotChangeCreditLimitOrCurrentBalance() {
        CardAccount cardAccount = accountWithAvailableCredit("6874.25");

        cardAccount.releaseCredit(new BigDecimal("125.75"));

        assertThat(cardAccount.getCreditLimit()).isEqualByComparingTo("10000.00");
        assertThat(cardAccount.getCurrentBalance()).isEqualByComparingTo("2000.00");
    }

    @Test
    void releaseCreditRejectsAmountExceedingCommittedExposure() {
        CardAccount cardAccount = accountWithAvailableCredit("9000.00");

        assertThatThrownBy(() -> cardAccount.releaseCredit(new BigDecimal("1000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Release amount exceeds committed credit exposure");
    }

    @Test
    void rejectedOverReleaseDoesNotMutateAvailableCredit() {
        CardAccount cardAccount = accountWithAvailableCredit("9000.00");

        assertThatThrownBy(() -> cardAccount.releaseCredit(new BigDecimal("1000.01")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("9000.00");
    }

    @Test
    void authorizationThenCreditLimitChangeThenReversalPreservesExposureChanges() {
        CardAccount cardAccount = accountWithAvailableCredit("7000.00");
        cardAccount.reserveCredit(new BigDecimal("125.75"));
        BigDecimal committedExposure = cardAccount.getCreditLimit()
                .subtract(cardAccount.getAvailableCredit());
        BigDecimal newCreditLimit = new BigDecimal("15000.00");
        cardAccount.update(
                newCreditLimit,
                newCreditLimit.subtract(committedExposure),
                CardAccountStatus.ACTIVE
        );

        cardAccount.releaseCredit(new BigDecimal("125.75"));

        assertThat(cardAccount.getCreditLimit()).isEqualByComparingTo("15000.00");
        assertThat(cardAccount.getCurrentBalance()).isEqualByComparingTo("2000.00");
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("12000.00");
        assertThat(cardAccount.getCreditLimit().subtract(cardAccount.getAvailableCredit()))
                .isEqualByComparingTo("3000.00");
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
