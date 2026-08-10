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

    @Test
    void postClearingIncreasesCurrentBalanceAndLeavesAvailableCreditUnchanged() {
        CardAccount cardAccount = new CardAccount(
                "ACC-630001", new BigDecimal("1000.00"), new BigDecimal("100.00"),
                new BigDecimal("650.00"), "USD", CardAccountStatus.ACTIVE
        );

        cardAccount.postClearing(new BigDecimal("150.00"));

        assertThat(cardAccount.getCurrentBalance()).isEqualByComparingTo("250.00");
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("650.00");
    }

    @Test
    void postClearingConvertsPendingExposureWithoutChangingTotalExposure() {
        CardAccount cardAccount = clearingAccount();
        BigDecimal totalBefore = totalExposure(cardAccount);

        cardAccount.postClearing(new BigDecimal("150.00"));

        assertThat(totalExposure(cardAccount)).isEqualByComparingTo(totalBefore);
        assertThat(pendingExposure(cardAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void multipleExposurePostingProducesExpectedBalances() {
        CardAccount cardAccount = clearingAccount();

        assertThat(pendingExposure(cardAccount)).isEqualByComparingTo("250.00");
        cardAccount.postClearing(new BigDecimal("150.00"));

        assertThat(cardAccount.getCurrentBalance()).isEqualByComparingTo("250.00");
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("650.00");
        assertThat(totalExposure(cardAccount)).isEqualByComparingTo("350.00");
        assertThat(pendingExposure(cardAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void postingMoreThanPendingAuthorizationExposureIsRejectedWithoutMutation() {
        CardAccount cardAccount = clearingAccount();

        assertThatThrownBy(() -> cardAccount.postClearing(new BigDecimal("250.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Clearing amount exceeds pending authorization exposure");
        assertThat(cardAccount.getCurrentBalance()).isEqualByComparingTo("100.00");
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("650.00");
    }

    @Test
    void authorizationThenCreditLimitIncreaseThenClearingPreservesExposureModel() {
        CardAccount cardAccount = new CardAccount(
                "ACC-630002", new BigDecimal("1000.00"), new BigDecimal("100.00"),
                new BigDecimal("750.00"), "USD", CardAccountStatus.ACTIVE
        );
        cardAccount.reserveCredit(new BigDecimal("100.00"));
        BigDecimal totalExposure = totalExposure(cardAccount);
        BigDecimal newCreditLimit = new BigDecimal("1500.00");
        cardAccount.update(
                newCreditLimit,
                newCreditLimit.subtract(totalExposure),
                CardAccountStatus.ACTIVE
        );

        cardAccount.postClearing(new BigDecimal("100.00"));

        assertThat(cardAccount.getCreditLimit()).isEqualByComparingTo("1500.00");
        assertThat(cardAccount.getCurrentBalance()).isEqualByComparingTo("200.00");
        assertThat(cardAccount.getAvailableCredit()).isEqualByComparingTo("1150.00");
        assertThat(totalExposure(cardAccount)).isEqualByComparingTo("350.00");
        assertThat(pendingExposure(cardAccount)).isEqualByComparingTo("150.00");
    }

    private static CardAccount clearingAccount() {
        return new CardAccount(
                "ACC-630001",
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("650.00"),
                "USD",
                CardAccountStatus.ACTIVE
        );
    }

    private static BigDecimal totalExposure(CardAccount cardAccount) {
        return cardAccount.getCreditLimit().subtract(cardAccount.getAvailableCredit());
    }

    private static BigDecimal pendingExposure(CardAccount cardAccount) {
        return totalExposure(cardAccount).subtract(cardAccount.getCurrentBalance());
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
