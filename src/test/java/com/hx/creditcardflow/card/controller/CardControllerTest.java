package com.hx.creditcardflow.card.controller;

import com.hx.creditcardflow.card.dto.CardCreateRequest;
import com.hx.creditcardflow.card.dto.CardResponse;
import com.hx.creditcardflow.card.dto.CardUpdateRequest;
import com.hx.creditcardflow.card.entity.CardStatus;
import com.hx.creditcardflow.card.exception.CardAccountNotEligibleForCardIssuanceException;
import com.hx.creditcardflow.card.exception.CardNotFoundException;
import com.hx.creditcardflow.card.exception.DuplicateCardReferenceException;
import com.hx.creditcardflow.card.exception.InvalidCardExpirationException;
import com.hx.creditcardflow.card.service.CardService;
import com.hx.creditcardflow.cardaccount.entity.CardAccountStatus;
import com.hx.creditcardflow.cardaccount.exception.CardAccountNotFoundException;
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

import java.time.Instant;
import java.time.YearMonth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private CardService cardService;

    @Test
    void shouldCreateCard() throws Exception {
        CardCreateRequest request = createRequest();
        CardResponse response = response(CardStatus.ACTIVE);
        when(cardService.createCard(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardReference").value("CARD-312001"))
                .andExpect(jsonPath("$.lastFour").value("0001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cardAccountNumber").value("ACC-312001"));

        verify(cardService).createCard(request);
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        CardCreateRequest request = new CardCreateRequest(" ", "12A", 0, 1999, " ");

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.cardReference").exists())
                .andExpect(jsonPath("$.validationErrors.lastFour").exists())
                .andExpect(jsonPath("$.validationErrors.expirationMonth").exists())
                .andExpect(jsonPath("$.validationErrors.expirationYear").exists())
                .andExpect(jsonPath("$.validationErrors.cardAccountNumber").exists());

        verify(cardService, never()).createCard(any(CardCreateRequest.class));
    }

    @Test
    void shouldReturnConflictForDuplicateCardReference() throws Exception {
        CardCreateRequest request = createRequest();
        when(cardService.createCard(request))
                .thenThrow(new DuplicateCardReferenceException("CARD-312001"));

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Card reference already exists: CARD-312001"))
                .andExpect(jsonPath("$.path").value("/api/v1/cards"));
    }

    @Test
    void shouldReuseCardAccountNotFoundMappingDuringIssuance() throws Exception {
        CardCreateRequest request = createRequest();
        when(cardService.createCard(request))
                .thenThrow(new CardAccountNotFoundException("ACC-312001"));

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card account not found with account number: ACC-312001"));
    }

    @Test
    void shouldReturnConflictForIneligibleCardAccount() throws Exception {
        CardCreateRequest request = createRequest();
        when(cardService.createCard(request))
                .thenThrow(new CardAccountNotEligibleForCardIssuanceException(
                        "ACC-312001", CardAccountStatus.SUSPENDED
                ));

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Card account is not eligible for card issuance: ACC-312001 with status SUSPENDED"));
    }

    @Test
    void shouldReturnBadRequestForInvalidExpiration() throws Exception {
        CardCreateRequest request = createRequest();
        YearMonth expiration = YearMonth.of(2025, 1);
        when(cardService.createCard(request))
                .thenThrow(new InvalidCardExpirationException(expiration));

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Card expiration is before the current month: 2025-01"));
    }

    @Test
    void shouldGetCard() throws Exception {
        when(cardService.getCard("CARD-312001")).thenReturn(response(CardStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/cards/{cardReference}", "CARD-312001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cardReference").value("CARD-312001"))
                .andExpect(jsonPath("$.lastFour").value("0001"))
                .andExpect(jsonPath("$.expirationMonth").value(12))
                .andExpect(jsonPath("$.expirationYear").value(2030))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cardAccountNumber").value("ACC-312001"));

        verify(cardService).getCard("CARD-312001");
    }

    @Test
    void shouldReturnNotFoundForMissingCard() throws Exception {
        when(cardService.getCard("CARD-NOT-FOUND"))
                .thenThrow(new CardNotFoundException("CARD-NOT-FOUND"));

        mockMvc.perform(get("/api/v1/cards/{cardReference}", "CARD-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card not found with card reference: CARD-NOT-FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/cards/CARD-NOT-FOUND"));
    }

    @Test
    void shouldUpdateCard() throws Exception {
        CardUpdateRequest request = new CardUpdateRequest(CardStatus.BLOCKED);
        when(cardService.updateCard("CARD-312001", request))
                .thenReturn(response(CardStatus.BLOCKED));

        mockMvc.perform(put("/api/v1/cards/{cardReference}", "CARD-312001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardReference").value("CARD-312001"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        verify(cardService).updateCard("CARD-312001", request);
    }

    @Test
    void shouldRejectInvalidUpdateRequest() throws Exception {
        CardUpdateRequest request = new CardUpdateRequest(null);

        mockMvc.perform(put("/api/v1/cards/{cardReference}", "CARD-312001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.status").exists());

        verify(cardService, never())
                .updateCard(any(String.class), any(CardUpdateRequest.class));
    }

    private static CardCreateRequest createRequest() {
        return new CardCreateRequest("CARD-312001", "0001", 12, 2030, "ACC-312001");
    }

    private static CardResponse response(CardStatus status) {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        return new CardResponse(
                1L,
                "CARD-312001",
                "0001",
                12,
                2030,
                status,
                "ACC-312001",
                now,
                now
        );
    }
}
