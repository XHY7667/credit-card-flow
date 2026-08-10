package com.hx.creditcardflow.common.exception;

import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.authorization.exception.DuplicateAuthorizationReferenceException;
import com.hx.creditcardflow.card.exception.CardAccountNotEligibleForCardIssuanceException;
import com.hx.creditcardflow.card.exception.CardNotFoundException;
import com.hx.creditcardflow.card.exception.DuplicateCardReferenceException;
import com.hx.creditcardflow.card.exception.InvalidCardExpirationException;
import com.hx.creditcardflow.cardaccount.exception.CardAccountNotFoundException;
import com.hx.creditcardflow.cardaccount.exception.DuplicateCardAccountNumberException;
import com.hx.creditcardflow.cardaccount.exception.InvalidCardAccountCreditLimitException;
import com.hx.creditcardflow.clearing.exception.ClearingAmountMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingCurrencyMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingNotAllowedException;
import com.hx.creditcardflow.clearing.exception.ClearingNotFoundException;
import com.hx.creditcardflow.clearing.exception.DuplicateClearingReferenceException;
import com.hx.creditcardflow.merchant.exception.DuplicateMerchantCodeException;
import com.hx.creditcardflow.merchant.exception.MerchantNotFoundException;
import com.hx.creditcardflow.reversal.exception.DuplicateReversalReferenceException;
import com.hx.creditcardflow.reversal.exception.IdempotencyKeyConflictException;
import com.hx.creditcardflow.reversal.exception.ReversalAmountMismatchException;
import com.hx.creditcardflow.reversal.exception.ReversalNotAllowedException;
import com.hx.creditcardflow.reversal.exception.ReversalNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClearingNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleClearingNotFound(
            ClearingNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler({
            DuplicateClearingReferenceException.class,
            ClearingNotAllowedException.class,
            ClearingAmountMismatchException.class,
            ClearingCurrencyMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleClearingConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(ReversalNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReversalNotFound(
            ReversalNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler({
            DuplicateReversalReferenceException.class,
            IdempotencyKeyConflictException.class,
            ReversalNotAllowedException.class,
            ReversalAmountMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleReversalConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(AuthorizationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthorizationNotFound(
            AuthorizationNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(DuplicateAuthorizationReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateAuthorizationReference(
            DuplicateAuthorizationReferenceException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCardNotFound(
            CardNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(DuplicateCardReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCardReference(
            DuplicateCardReferenceException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(CardAccountNotEligibleForCardIssuanceException.class)
    public ResponseEntity<ApiErrorResponse> handleCardAccountNotEligibleForCardIssuance(
            CardAccountNotEligibleForCardIssuanceException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(InvalidCardExpirationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCardExpiration(
            InvalidCardExpirationException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(CardAccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCardAccountNotFound(
            CardAccountNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(DuplicateCardAccountNumberException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCardAccountNumber(
            DuplicateCardAccountNumberException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(InvalidCardAccountCreditLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCardAccountCreditLimit(
            InvalidCardAccountCreditLimitException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(MerchantNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMerchantNotFound(
            MerchantNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(DuplicateMerchantCodeException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateMerchantCode(
            DuplicateMerchantCodeException exception,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            validationErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                validationErrors
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> validationErrors
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                validationErrors
        );
        return ResponseEntity.status(status).body(response);
    }
}
