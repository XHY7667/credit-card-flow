package com.hx.creditcardflow.reversal.controller;

import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.common.exception.GlobalExceptionHandler;
import com.hx.creditcardflow.reversal.dto.ReversalCreateRequest;
import com.hx.creditcardflow.reversal.dto.ReversalResponse;
import com.hx.creditcardflow.reversal.entity.ReversalStatus;
import com.hx.creditcardflow.reversal.exception.DuplicateReversalReferenceException;
import com.hx.creditcardflow.reversal.exception.IdempotencyKeyConflictException;
import com.hx.creditcardflow.reversal.exception.ReversalAmountMismatchException;
import com.hx.creditcardflow.reversal.exception.ReversalNotAllowedException;
import com.hx.creditcardflow.reversal.exception.ReversalNotFoundException;
import com.hx.creditcardflow.reversal.service.ReversalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReversalController.class)
@Import(GlobalExceptionHandler.class)
class ReversalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @MockitoBean ReversalService reversalService;

    @Test
    void firstSuccessfulReversalReturnsCreatedAndBusinessData() throws Exception {
        when(reversalService.createReversal("REV-KEY-001", request())).thenReturn(response());

        postReversal("REV-KEY-001", request())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalReference").value("REV-550001"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-550001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(reversalService).createReversal("REV-KEY-001", request());
    }

    @Test
    void replayedSuccessfulReversalAlsoReturnsCreated() throws Exception {
        when(reversalService.createReversal("REV-KEY-001", request())).thenReturn(response());
        postReversal("REV-KEY-001", request()).andExpect(status().isCreated());
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/reversals").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
        verify(reversalService, never()).createReversal(any(), any());
    }

    @Test
    void blankIdempotencyKeyReturnsBadRequest() throws Exception {
        when(reversalService.createReversal(" ", request())).thenThrow(new IllegalArgumentException(
                "Idempotency key must be present and not exceed 100 characters"));
        postReversal(" ", request()).andExpect(status().isBadRequest());
    }

    @Test
    void invalidBodyReturnsBadRequest() throws Exception {
        ReversalCreateRequest invalid = new ReversalCreateRequest(" ", " ", BigDecimal.ZERO);
        postReversal("REV-KEY-001", invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
        verify(reversalService, never()).createReversal(any(), any());
    }

    @Test void missingAuthorizationReturnsNotFound() throws Exception {
        assertPostException(new AuthorizationNotFoundException("AUTH-550001"), 404);
    }

    @Test void duplicateReferenceReturnsConflict() throws Exception {
        assertPostException(new DuplicateReversalReferenceException("REV-550001"), 409);
    }

    @Test void idempotencyConflictReturnsConflict() throws Exception {
        assertPostException(new IdempotencyKeyConflictException("REV-KEY-001"), 409);
    }

    @Test void reversalNotAllowedReturnsConflict() throws Exception {
        assertPostException(new ReversalNotAllowedException("AUTH-550001",
                com.hx.creditcardflow.authorization.entity.AuthorizationStatus.REVERSED), 409);
    }

    @Test void amountMismatchReturnsConflict() throws Exception {
        assertPostException(new ReversalAmountMismatchException(
                new BigDecimal("1.00"), new BigDecimal("125.75")), 409);
    }

    @Test
    void getExistingReversalReturnsOk() throws Exception {
        when(reversalService.getReversal("REV-550001")).thenReturn(response());
        mockMvc.perform(get("/api/v1/reversals/{reversalReference}", "REV-550001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalReference").value("REV-550001"));
    }

    @Test
    void getMissingReversalReturnsNotFound() throws Exception {
        when(reversalService.getReversal("REV-NOT-FOUND"))
                .thenThrow(new ReversalNotFoundException("REV-NOT-FOUND"));
        mockMvc.perform(get("/api/v1/reversals/{reversalReference}", "REV-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    private void assertPostException(RuntimeException exception, int expectedStatus) throws Exception {
        when(reversalService.createReversal("REV-KEY-001", request())).thenThrow(exception);
        postReversal("REV-KEY-001", request()).andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions postReversal(
            String key, ReversalCreateRequest body) throws Exception {
        return mockMvc.perform(post("/api/v1/reversals")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private static ReversalCreateRequest request() {
        return new ReversalCreateRequest("REV-550001", "AUTH-550001", new BigDecimal("125.75"));
    }

    private static ReversalResponse response() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        return new ReversalResponse(1L, "REV-550001", "AUTH-550001",
                new BigDecimal("125.75"), ReversalStatus.COMPLETED, now, now);
    }
}
