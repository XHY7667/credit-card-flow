package com.hx.creditcardflow.merchant.dto;

import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantDtoValidationTest {

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
    void validMerchantCreateRequestHasNoValidationViolations() {
        MerchantCreateRequest request = validCreateRequest();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void blankMerchantCodeIsRejected() {
        MerchantCreateRequest request = createRequestWithMerchantCode(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("merchantCode");
    }

    @Test
    void lowercaseOrSpaceContainingMerchantCodeIsRejected() {
        Set<ConstraintViolation<MerchantCreateRequest>> lowercaseViolations =
                validator.validate(createRequestWithMerchantCode("m100001"));
        Set<ConstraintViolation<MerchantCreateRequest>> spaceViolations =
                validator.validate(createRequestWithMerchantCode("M 100001"));

        assertThat(lowercaseViolations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("merchantCode");
        assertThat(spaceViolations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("merchantCode");
    }

    @Test
    void merchantCategoryCodeThatIsNotExactlyFourDigitsIsRejected() {
        MerchantCreateRequest request = new MerchantCreateRequest(
                "M100001",
                "Northstar Market Group LLC",
                "Northstar Market",
                "541",
                "US",
                MerchantStatus.ACTIVE
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("merchantCategoryCode");
    }

    @Test
    void lowercaseOrThreeCharacterCountryCodeIsRejected() {
        MerchantCreateRequest lowercaseRequest = createRequestWithCountryCode("us");
        MerchantCreateRequest threeCharacterRequest = createRequestWithCountryCode("USA");

        assertThat(validator.validate(lowercaseRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("countryCode");
        assertThat(validator.validate(threeCharacterRequest))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("countryCode");
    }

    @Test
    void nullStatusIsRejected() {
        MerchantCreateRequest request = new MerchantCreateRequest(
                "M100001",
                "Northstar Market Group LLC",
                "Northstar Market",
                "5411",
                "US",
                null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("status");
    }

    @Test
    void merchantUpdateRequestDoesNotContainMerchantCodeComponent() {
        String[] componentNames = Arrays.stream(MerchantUpdateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);

        assertThat(componentNames).doesNotContain("merchantCode");
    }

    private static MerchantCreateRequest validCreateRequest() {
        return new MerchantCreateRequest(
                "M100001",
                "Northstar Market Group LLC",
                "Northstar Market",
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );
    }

    private static MerchantCreateRequest createRequestWithMerchantCode(String merchantCode) {
        MerchantCreateRequest validRequest = validCreateRequest();
        return new MerchantCreateRequest(
                merchantCode,
                validRequest.legalName(),
                validRequest.displayName(),
                validRequest.merchantCategoryCode(),
                validRequest.countryCode(),
                validRequest.status()
        );
    }

    private static MerchantCreateRequest createRequestWithCountryCode(String countryCode) {
        MerchantCreateRequest validRequest = validCreateRequest();
        return new MerchantCreateRequest(
                validRequest.merchantCode(),
                validRequest.legalName(),
                validRequest.displayName(),
                validRequest.merchantCategoryCode(),
                countryCode,
                validRequest.status()
        );
    }
}
