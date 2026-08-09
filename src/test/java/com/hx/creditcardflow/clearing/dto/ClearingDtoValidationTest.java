package com.hx.creditcardflow.clearing.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ClearingDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void fullyValidRequestHasNoValidationViolations() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void blankClearingReferenceIsRejected() {
        assertViolationOn(request(" ", "AUTH-620001", "125.75", "USD"), "clearingReference");
    }

    @Test
    void clearingReferenceLongerThanFiftyCharactersIsRejected() {
        assertViolationOn(request("C".repeat(51), "AUTH-620001", "125.75", "USD"),
                "clearingReference");
    }

    @Test
    void blankAuthorizationReferenceIsRejected() {
        assertViolationOn(request("CLR-620001", " ", "125.75", "USD"),
                "authorizationReference");
    }

    @Test
    void authorizationReferenceLongerThanFiftyCharactersIsRejected() {
        assertViolationOn(request("CLR-620001", "A".repeat(51), "125.75", "USD"),
                "authorizationReference");
    }

    @Test
    void nullAmountIsRejected() {
        ClearingCreateRequest request = new ClearingCreateRequest(
                "CLR-620001", "AUTH-620001", null, "USD"
        );

        assertViolationOn(request, "amount");
    }

    @Test
    void zeroAmountIsRejected() {
        assertAmountViolation("0.00");
    }

    @Test
    void negativeAmountIsRejected() {
        assertAmountViolation("-1.00");
    }

    @Test
    void amountWithMoreThanTwoFractionalDigitsIsRejected() {
        assertAmountViolation("125.751");
    }

    @Test
    void amountExceedingNumericIntegerCapacityIsRejected() {
        assertAmountViolation("100000000000000000.00");
    }

    @Test
    void uppercaseThreeLetterCurrencyIsAccepted() {
        assertThat(validator.validate(request(
                "CLR-620001", "AUTH-620001", "125.75", "EUR"))).isEmpty();
    }

    @Test
    void lowercaseCurrencyIsRejected() {
        assertCurrencyViolation("usd");
    }

    @Test
    void twoCharacterCurrencyIsRejected() {
        assertCurrencyViolation("US");
    }

    @Test
    void fourCharacterCurrencyIsRejected() {
        assertCurrencyViolation("USDD");
    }

    @Test
    void nonAlphabeticCurrencyIsRejected() {
        assertCurrencyViolation("U1D");
    }

    private static ClearingCreateRequest validRequest() {
        return request("CLR-620001", "AUTH-620001", "125.75", "USD");
    }

    private static ClearingCreateRequest request(
            String clearingReference,
            String authorizationReference,
            String amount,
            String currencyCode
    ) {
        return new ClearingCreateRequest(
                clearingReference,
                authorizationReference,
                new BigDecimal(amount),
                currencyCode
        );
    }

    private static void assertAmountViolation(String amount) {
        assertViolationOn(request("CLR-620001", "AUTH-620001", amount, "USD"), "amount");
    }

    private static void assertCurrencyViolation(String currencyCode) {
        assertViolationOn(request("CLR-620001", "AUTH-620001", "125.75", currencyCode),
                "currencyCode");
    }

    private static void assertViolationOn(Object request, String propertyName) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyName);
    }
}
