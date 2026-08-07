package com.hx.creditcardflow.cardaccount.dto;

import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CardAccountDtoValidationTest {

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
    void validCreateRequestHasNoValidationViolations() {
        assertThat(validator.validate(validCreateRequest())).isEmpty();
    }

    @Test
    void blankAccountNumberIsRejected() {
        CardAccountCreateRequest request = new CardAccountCreateRequest(
                " ", new BigDecimal("5000.00"), "USD"
        );

        assertViolationOn(request, "accountNumber");
    }

    @Test
    void accountNumberLongerThanThirtyCharactersIsRejected() {
        CardAccountCreateRequest request = new CardAccountCreateRequest(
                "A".repeat(31), new BigDecimal("5000.00"), "USD"
        );

        assertViolationOn(request, "accountNumber");
    }

    @Test
    void nullCreateCreditLimitIsRejected() {
        CardAccountCreateRequest request = new CardAccountCreateRequest("ACC-330001", null, "USD");

        assertViolationOn(request, "creditLimit");
    }

    @Test
    void zeroCreateCreditLimitIsRejected() {
        CardAccountCreateRequest request = createRequestWithCreditLimit("0.00");

        assertViolationOn(request, "creditLimit");
    }

    @Test
    void negativeCreateCreditLimitIsRejected() {
        CardAccountCreateRequest request = createRequestWithCreditLimit("-1.00");

        assertViolationOn(request, "creditLimit");
    }

    @Test
    void createCreditLimitWithMoreThanTwoFractionalDigitsIsRejected() {
        CardAccountCreateRequest request = createRequestWithCreditLimit("100.001");

        assertViolationOn(request, "creditLimit");
    }

    @Test
    void uppercaseThreeLetterCurrencyCodeIsAccepted() {
        CardAccountCreateRequest request = createRequestWithCurrencyCode("EUR");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void lowercaseCurrencyCodeIsRejected() {
        CardAccountCreateRequest request = createRequestWithCurrencyCode("usd");

        assertViolationOn(request, "currencyCode");
    }

    @Test
    void currencyCodeWithIncorrectLengthIsRejected() {
        CardAccountCreateRequest request = createRequestWithCurrencyCode("US");

        assertViolationOn(request, "currencyCode");
    }

    @Test
    void validUpdateRequestHasNoValidationViolations() {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("6000.00"), CardAccountStatus.ACTIVE
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nullUpdateCreditLimitIsRejected() {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(null, CardAccountStatus.ACTIVE);

        assertViolationOn(request, "creditLimit");
    }

    @Test
    void invalidUpdateCreditLimitIsRejected() {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("100.001"), CardAccountStatus.ACTIVE
        );

        assertViolationOn(request, "creditLimit");
    }

    @Test
    void nullUpdateStatusIsRejected() {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(new BigDecimal("6000.00"), null);

        assertViolationOn(request, "status");
    }

    private static CardAccountCreateRequest validCreateRequest() {
        return new CardAccountCreateRequest("ACC-330001", new BigDecimal("5000.00"), "USD");
    }

    private static CardAccountCreateRequest createRequestWithCreditLimit(String creditLimit) {
        CardAccountCreateRequest validRequest = validCreateRequest();
        return new CardAccountCreateRequest(
                validRequest.accountNumber(),
                new BigDecimal(creditLimit),
                validRequest.currencyCode()
        );
    }

    private static CardAccountCreateRequest createRequestWithCurrencyCode(String currencyCode) {
        CardAccountCreateRequest validRequest = validCreateRequest();
        return new CardAccountCreateRequest(
                validRequest.accountNumber(),
                validRequest.creditLimit(),
                currencyCode
        );
    }

    private static void assertViolationOn(Object request, String propertyName) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyName);
    }
}
