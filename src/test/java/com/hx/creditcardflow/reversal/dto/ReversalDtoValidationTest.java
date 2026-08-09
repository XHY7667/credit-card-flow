package com.hx.creditcardflow.reversal.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReversalDtoValidationTest {

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
    void blankReversalReferenceIsRejected() {
        assertViolationOn(request(" ", "AUTH-520001", "125.75"), "reversalReference");
    }

    @Test
    void reversalReferenceLongerThanFiftyCharactersIsRejected() {
        assertViolationOn(request("R".repeat(51), "AUTH-520001", "125.75"), "reversalReference");
    }

    @Test
    void blankAuthorizationReferenceIsRejected() {
        assertViolationOn(request("REV-520001", " ", "125.75"), "authorizationReference");
    }

    @Test
    void authorizationReferenceLongerThanFiftyCharactersIsRejected() {
        assertViolationOn(request("REV-520001", "A".repeat(51), "125.75"), "authorizationReference");
    }

    @Test
    void nullAmountIsRejected() {
        ReversalCreateRequest request = new ReversalCreateRequest(
                "REV-520001", "AUTH-520001", null
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

    private static ReversalCreateRequest validRequest() {
        return request("REV-520001", "AUTH-520001", "125.75");
    }

    private static ReversalCreateRequest request(
            String reversalReference,
            String authorizationReference,
            String amount
    ) {
        return new ReversalCreateRequest(
                reversalReference,
                authorizationReference,
                new BigDecimal(amount)
        );
    }

    private static void assertAmountViolation(String amount) {
        assertViolationOn(request("REV-520001", "AUTH-520001", amount), "amount");
    }

    private static void assertViolationOn(Object request, String propertyName) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyName);
    }
}
