package com.hx.creditcardflow.clearing.controller;

import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.clearing.dto.ClearingCreateRequest;
import com.hx.creditcardflow.clearing.dto.ClearingResponse;
import com.hx.creditcardflow.clearing.entity.ClearingStatus;
import com.hx.creditcardflow.clearing.exception.ClearingAmountMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingCurrencyMismatchException;
import com.hx.creditcardflow.clearing.exception.ClearingNotAllowedException;
import com.hx.creditcardflow.clearing.exception.ClearingNotFoundException;
import com.hx.creditcardflow.clearing.exception.DuplicateClearingReferenceException;
import com.hx.creditcardflow.clearing.service.ClearingService;
import com.hx.creditcardflow.common.exception.GlobalExceptionHandler;
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

@WebMvcTest(ClearingController.class)
@Import(GlobalExceptionHandler.class)
class ClearingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @MockitoBean ClearingService clearingService;

    @Test
    void successfulPostReturnsCreatedBusinessDataAndDelegates() throws Exception {
        when(clearingService.createClearing(request())).thenReturn(response());

        postClearing(request())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clearingReference").value("CLR-640001"))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-640001"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.status").value("POSTED"));

        verify(clearingService).createClearing(request());
    }

    @Test
    void invalidBodyReturnsBadRequestWithoutCallingService() throws Exception {
        ClearingCreateRequest invalid = new ClearingCreateRequest(" ", " ", BigDecimal.ZERO, "usd");

        postClearing(invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
        verify(clearingService, never()).createClearing(any(ClearingCreateRequest.class));
    }

    @Test void missingAuthorizationReturnsNotFound() throws Exception {
        assertPostException(new AuthorizationNotFoundException("AUTH-640001"), 404);
    }

    @Test void duplicateReferenceReturnsConflict() throws Exception {
        assertPostException(new DuplicateClearingReferenceException("CLR-640001"), 409);
    }

    @Test void clearingNotAllowedReturnsConflict() throws Exception {
        assertPostException(new ClearingNotAllowedException(
                "AUTH-640001", AuthorizationStatus.CLEARED), 409);
    }

    @Test void amountMismatchReturnsConflict() throws Exception {
        assertPostException(new ClearingAmountMismatchException(
                new BigDecimal("100.00"), new BigDecimal("200.00")), 409);
    }

    @Test void currencyMismatchReturnsConflict() throws Exception {
        assertPostException(new ClearingCurrencyMismatchException("EUR", "USD"), 409);
    }

    @Test
    void getExistingClearingReturnsOk() throws Exception {
        when(clearingService.getClearing("CLR-640001")).thenReturn(response());

        mockMvc.perform(get("/api/v1/clearings/{clearingReference}", "CLR-640001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearingReference").value("CLR-640001"))
                .andExpect(jsonPath("$.status").value("POSTED"));
        verify(clearingService).getClearing("CLR-640001");
    }

    @Test
    void getMissingClearingReturnsNotFound() throws Exception {
        when(clearingService.getClearing("CLR-NOT-FOUND"))
                .thenThrow(new ClearingNotFoundException("CLR-NOT-FOUND"));

        mockMvc.perform(get("/api/v1/clearings/{clearingReference}", "CLR-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    private void assertPostException(RuntimeException exception, int expectedStatus) throws Exception {
        when(clearingService.createClearing(request())).thenThrow(exception);
        postClearing(request()).andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions postClearing(
            ClearingCreateRequest body) throws Exception {
        return mockMvc.perform(post("/api/v1/clearings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private static ClearingCreateRequest request() {
        return new ClearingCreateRequest(
                "CLR-640001", "AUTH-640001", new BigDecimal("200.00"), "USD"
        );
    }

    private static ClearingResponse response() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        return new ClearingResponse(
                1L, "CLR-640001", "AUTH-640001", new BigDecimal("200.00"),
                "USD", ClearingStatus.POSTED, now, now
        );
    }
}
