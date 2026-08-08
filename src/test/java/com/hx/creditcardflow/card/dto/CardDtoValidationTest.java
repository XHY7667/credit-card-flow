package com.hx.creditcardflow.card.dto;

import com.hx.creditcardflow.card.entity.CardStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardDtoValidationTest {

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
    void blankCardReferenceIsRejected() {
        assertViolationOn(createRequest(" ", "4242", 12, 2030, "ACC-310001"), "cardReference");
    }

    @Test
    void cardReferenceLongerThanThirtyCharactersIsRejected() {
        assertViolationOn(
                createRequest("C".repeat(31), "4242", 12, 2030, "ACC-310001"),
                "cardReference"
        );
    }

    @Test
    void exactlyFourDigitLastFourIsAccepted() {
        assertThat(validator.validate(
                createRequest("CARD-310001", "4242", 12, 2030, "ACC-310001")
        )).isEmpty();
    }

    @Test
    void lastFourShorterThanFourDigitsIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "424", 12, 2030, "ACC-310001"), "lastFour");
    }

    @Test
    void lastFourLongerThanFourDigitsIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "42424", 12, 2030, "ACC-310001"), "lastFour");
    }

    @Test
    void nonnumericLastFourIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "42A2", 12, 2030, "ACC-310001"), "lastFour");
    }

    @Test
    void leadingZeroLastFourIsAccepted() {
        assertThat(validator.validate(
                createRequest("CARD-310001", "0001", 12, 2030, "ACC-310001")
        )).isEmpty();
    }

    @Test
    void nullExpirationMonthIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "4242", null, 2030, "ACC-310001"), "expirationMonth");
    }

    @Test
    void expirationMonthBelowOneIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "4242", 0, 2030, "ACC-310001"), "expirationMonth");
    }

    @Test
    void expirationMonthAboveTwelveIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "4242", 13, 2030, "ACC-310001"), "expirationMonth");
    }

    @Test
    void nullExpirationYearIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "4242", 12, null, "ACC-310001"), "expirationYear");
    }

    @Test
    void expirationYearBelowFourDigitRangeIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "4242", 12, 1999, "ACC-310001"), "expirationYear");
    }

    @Test
    void blankCardAccountNumberIsRejected() {
        assertViolationOn(createRequest("CARD-310001", "4242", 12, 2030, " "), "cardAccountNumber");
    }

    @Test
    void validUpdateRequestHasNoValidationViolations() {
        assertThat(validator.validate(new CardUpdateRequest(CardStatus.BLOCKED))).isEmpty();
    }

    @Test
    void nullUpdateStatusIsRejected() {
        assertViolationOn(new CardUpdateRequest(null), "status");
    }

    private static CardCreateRequest validCreateRequest() {
        return createRequest("CARD-310001", "4242", 12, 2030, "ACC-310001");
    }

    private static CardCreateRequest createRequest(
            String cardReference,
            String lastFour,
            Integer expirationMonth,
            Integer expirationYear,
            String cardAccountNumber
    ) {
        return new CardCreateRequest(
                cardReference,
                lastFour,
                expirationMonth,
                expirationYear,
                cardAccountNumber
        );
    }

    private static void assertViolationOn(Object request, String propertyName) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyName);
    }
}
