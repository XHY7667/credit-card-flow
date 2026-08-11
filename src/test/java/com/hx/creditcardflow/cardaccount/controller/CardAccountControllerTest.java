package com.hx.creditcardflow.cardaccount.controller;

import com.hx.creditcardflow.cardaccount.dto.CardAccountCreateRequest;
import com.hx.creditcardflow.cardaccount.dto.CardAccountResponse;
import com.hx.creditcardflow.cardaccount.dto.CardAccountUpdateRequest;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.exception.CardAccountNotFoundException;
import com.hx.creditcardflow.cardaccount.exception.DuplicateCardAccountNumberException;
import com.hx.creditcardflow.cardaccount.exception.InvalidCardAccountCreditLimitException;
import com.hx.creditcardflow.cardaccount.service.CardAccountService;
import com.hx.creditcardflow.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CardAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private CardAccountService cardAccountService;

    @Test
    void shouldCreateCardAccount() throws Exception {
        CardAccountCreateRequest request = createRequest("ACC-350001");
        CardAccountResponse response = response(
                1L, "ACC-350001", "10000.00", "0.00", "10000.00", CardAccountStatus.ACTIVE
        );
        when(cardAccountService.createCardAccount(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNumber").value("ACC-350001"))
                .andExpect(jsonPath("$.creditLimit").value(10000.00))
                .andExpect(jsonPath("$.availableCredit").value(10000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(cardAccountService).createCardAccount(request);
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        CardAccountCreateRequest request = new CardAccountCreateRequest(" ", BigDecimal.ZERO, "usd");

        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.accountNumber").exists())
                .andExpect(jsonPath("$.validationErrors.creditLimit").exists())
                .andExpect(jsonPath("$.validationErrors.currencyCode").exists());

        verify(cardAccountService, never()).createCardAccount(any(CardAccountCreateRequest.class));
    }

    @Test
    void shouldReturnConflictForDuplicateAccountNumber() throws Exception {
        CardAccountCreateRequest request = createRequest("ACC-350002");
        when(cardAccountService.createCardAccount(request))
                .thenThrow(new DuplicateCardAccountNumberException("ACC-350002"));

        mockMvc.perform(post("/api/v1/card-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Card account number already exists: ACC-350002"))
                .andExpect(jsonPath("$.path").value("/api/v1/card-accounts"));
    }

    @Test
    void shouldGetCardAccount() throws Exception {
        CardAccountResponse response = response(
                3L, "ACC-350003", "10000.00", "2000.00", "7000.00", CardAccountStatus.ACTIVE
        );
        when(cardAccountService.getCardAccount("ACC-350003")).thenReturn(response);

        mockMvc.perform(get("/api/v1/card-accounts/{accountNumber}", "ACC-350003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.accountNumber").value("ACC-350003"))
                .andExpect(jsonPath("$.currentBalance").value(2000.00))
                .andExpect(jsonPath("$.availableCredit").value(7000.00));

        verify(cardAccountService).getCardAccount("ACC-350003");
    }

    @Test
    void shouldReturnNotFoundForMissingCardAccount() throws Exception {
        when(cardAccountService.getCardAccount("ACC-NOT-FOUND"))
                .thenThrow(new CardAccountNotFoundException("ACC-NOT-FOUND"));

        mockMvc.perform(get("/api/v1/card-accounts/{accountNumber}", "ACC-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card account not found with account number: ACC-NOT-FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/card-accounts/ACC-NOT-FOUND"));
    }

    @Test
    void shouldUpdateCardAccount() throws Exception {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("15000.00"), CardAccountStatus.SUSPENDED
        );
        CardAccountResponse response = response(
                4L, "ACC-350004", "15000.00", "2000.00", "12000.00", CardAccountStatus.SUSPENDED
        );
        when(cardAccountService.updateCardAccount("ACC-350004", request)).thenReturn(response);

        mockMvc.perform(put("/api/v1/card-accounts/{accountNumber}", "ACC-350004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-350004"))
                .andExpect(jsonPath("$.creditLimit").value(15000.00))
                .andExpect(jsonPath("$.availableCredit").value(12000.00))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(cardAccountService).updateCardAccount("ACC-350004", request);
    }

    @Test
    void shouldRejectInvalidUpdateRequest() throws Exception {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(BigDecimal.ZERO, null);

        mockMvc.perform(put("/api/v1/card-accounts/{accountNumber}", "ACC-350005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.creditLimit").exists())
                .andExpect(jsonPath("$.validationErrors.status").exists());

        verify(cardAccountService, never())
                .updateCardAccount(any(String.class), any(CardAccountUpdateRequest.class));
    }

    @Test
    void shouldReturnBadRequestForInvalidCreditLimitReduction() throws Exception {
        CardAccountUpdateRequest request = new CardAccountUpdateRequest(
                new BigDecimal("2999.99"), CardAccountStatus.ACTIVE
        );
        when(cardAccountService.updateCardAccount("ACC-350006", request))
                .thenThrow(new InvalidCardAccountCreditLimitException(
                        new BigDecimal("2999.99"), new BigDecimal("3000.00")
                ));

        mockMvc.perform(put("/api/v1/card-accounts/{accountNumber}", "ACC-350006")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Card account credit limit 2999.99 cannot be below committed exposure 3000.00"))
                .andExpect(jsonPath("$.path").value("/api/v1/card-accounts/ACC-350006"));
    }

    private static CardAccountCreateRequest createRequest(String accountNumber) {
        return new CardAccountCreateRequest(accountNumber, new BigDecimal("10000.00"), "USD");
    }

    private static CardAccountResponse response(
            Long id,
            String accountNumber,
            String creditLimit,
            String currentBalance,
            String availableCredit,
            CardAccountStatus status
    ) {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return new CardAccountResponse(
                id,
                accountNumber,
                new BigDecimal(creditLimit),
                new BigDecimal(currentBalance),
                new BigDecimal(availableCredit),
                "USD",
                status,
                0L,
                now,
                now
        );
    }
}
