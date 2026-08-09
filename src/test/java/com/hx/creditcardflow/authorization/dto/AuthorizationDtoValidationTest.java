package com.hx.creditcardflow.authorization.dto;

import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationDtoValidationTest {

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
    void blankAuthorizationReferenceIsRejected() {
        assertViolationOn(request(" ", "CARD-420001", "MER-420001", "10.00", "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS), "authorizationReference");
    }

    @Test
    void authorizationReferenceLongerThanFiftyCharactersIsRejected() {
        assertViolationOn(request("A".repeat(51), "CARD-420001", "MER-420001", "10.00", "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS), "authorizationReference");
    }

    @Test
    void blankCardReferenceIsRejected() {
        assertViolationOn(request("AUTH-420001", " ", "MER-420001", "10.00", "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS), "cardReference");
    }

    @Test
    void blankMerchantCodeIsRejected() {
        assertViolationOn(request("AUTH-420001", "CARD-420001", " ", "10.00", "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS), "merchantCode");
    }

    @Test
    void nullAmountIsRejected() {
        AuthorizationCreateRequest request = new AuthorizationCreateRequest(
                "AUTH-420001", "CARD-420001", "MER-420001", null, "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS
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
    void amountWithMoreThanTwoDecimalPlacesIsRejected() {
        assertAmountViolation("10.001");
    }

    @Test
    void amountExceedingNumericIntegerCapacityIsRejected() {
        assertAmountViolation("100000000000000000.00");
    }

    @Test
    void usdCurrencyIsAccepted() {
        assertThat(validator.validate(request("AUTH-420001", "CARD-420001", "MER-420001", "10.00", "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS))).isEmpty();
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

    @Test
    void nullAuthorizationTypeIsRejected() {
        assertViolationOn(request("AUTH-420001", "CARD-420001", "MER-420001", "10.00", "USD",
                null, AuthorizationChannel.POS), "authorizationType");
    }

    @Test
    void nullChannelIsRejected() {
        assertViolationOn(request("AUTH-420001", "CARD-420001", "MER-420001", "10.00", "USD",
                AuthorizationType.PURCHASE, null), "channel");
    }

    private static AuthorizationCreateRequest validRequest() {
        return request("AUTH-420001", "CARD-420001", "MER-420001", "125.75", "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.PAYPAL);
    }

    private static AuthorizationCreateRequest request(
            String authorizationReference,
            String cardReference,
            String merchantCode,
            String amount,
            String currencyCode,
            AuthorizationType authorizationType,
            AuthorizationChannel channel
    ) {
        return new AuthorizationCreateRequest(
                authorizationReference,
                cardReference,
                merchantCode,
                new BigDecimal(amount),
                currencyCode,
                authorizationType,
                channel
        );
    }

    private static void assertAmountViolation(String amount) {
        assertViolationOn(request("AUTH-420001", "CARD-420001", "MER-420001", amount, "USD",
                AuthorizationType.PURCHASE, AuthorizationChannel.POS), "amount");
    }

    private static void assertCurrencyViolation(String currencyCode) {
        assertViolationOn(request("AUTH-420001", "CARD-420001", "MER-420001", "10.00", currencyCode,
                AuthorizationType.PURCHASE, AuthorizationChannel.POS), "currencyCode");
    }

    private static void assertViolationOn(Object request, String propertyName) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyName);
    }
}
