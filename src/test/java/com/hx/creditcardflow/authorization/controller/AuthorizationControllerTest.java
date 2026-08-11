package com.hx.creditcardflow.authorization.controller;

import com.hx.creditcardflow.authorization.dto.AuthorizationCreateRequest;
import com.hx.creditcardflow.authorization.dto.AuthorizationResponse;
import com.hx.creditcardflow.authorization.entity.AuthorizationChannel;
import com.hx.creditcardflow.authorization.entity.AuthorizationStatus;
import com.hx.creditcardflow.authorization.entity.AuthorizationType;
import com.hx.creditcardflow.authorization.exception.AuthorizationNotFoundException;
import com.hx.creditcardflow.authorization.exception.DuplicateAuthorizationReferenceException;
import com.hx.creditcardflow.authorization.service.AuthorizationService;
import com.hx.creditcardflow.card.exception.CardNotFoundException;
import com.hx.creditcardflow.common.exception.GlobalExceptionHandler;
import com.hx.creditcardflow.merchant.exception.MerchantNotFoundException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthorizationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    void approvedAuthorizationReturnsCreatedWithExpectedBusinessData() throws Exception {
        AuthorizationCreateRequest request = validRequest();
        when(authorizationService.createAuthorization(request))
                .thenReturn(response(AuthorizationStatus.APPROVED));

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-440001"))
                .andExpect(jsonPath("$.cardReference").value("CARD-440001"))
                .andExpect(jsonPath("$.merchantCode").value("MER-440001"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(authorizationService).createAuthorization(request);
    }

    @Test
    void declinedAuthorizationStillReturnsCreated() throws Exception {
        AuthorizationCreateRequest request = validRequest();
        when(authorizationService.createAuthorization(request))
                .thenReturn(response(AuthorizationStatus.DECLINED));

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void invalidRequestReturnsBadRequestWithoutCallingService() throws Exception {
        AuthorizationCreateRequest request = new AuthorizationCreateRequest(
                " ", " ", " ", BigDecimal.ZERO, "usd", null, null
        );

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.authorizationReference").exists())
                .andExpect(jsonPath("$.validationErrors.cardReference").exists())
                .andExpect(jsonPath("$.validationErrors.merchantCode").exists())
                .andExpect(jsonPath("$.validationErrors.amount").exists())
                .andExpect(jsonPath("$.validationErrors.currencyCode").exists())
                .andExpect(jsonPath("$.validationErrors.authorizationType").exists())
                .andExpect(jsonPath("$.validationErrors.channel").exists());

        verify(authorizationService, never()).createAuthorization(any(AuthorizationCreateRequest.class));
    }

    @Test
    void missingCardReturnsNotFound() throws Exception {
        AuthorizationCreateRequest request = validRequest();
        when(authorizationService.createAuthorization(request))
                .thenThrow(new CardNotFoundException("CARD-440001"));

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Card not found with card reference: CARD-440001"));
    }

    @Test
    void missingMerchantReturnsNotFound() throws Exception {
        AuthorizationCreateRequest request = validRequest();
        when(authorizationService.createAuthorization(request))
                .thenThrow(new MerchantNotFoundException("MER-440001"));

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Merchant not found with merchant code: MER-440001"));
    }

    @Test
    void duplicateAuthorizationReferenceReturnsConflict() throws Exception {
        AuthorizationCreateRequest request = validRequest();
        when(authorizationService.createAuthorization(request))
                .thenThrow(new DuplicateAuthorizationReferenceException("AUTH-440001"));

        mockMvc.perform(post("/api/v1/authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Authorization reference already exists: AUTH-440001"))
                .andExpect(jsonPath("$.path").value("/api/v1/authorizations"));
    }

    @Test
    void getExistingAuthorizationReturnsExpectedData() throws Exception {
        when(authorizationService.getAuthorization("AUTH-440001"))
                .thenReturn(response(AuthorizationStatus.APPROVED));

        mockMvc.perform(get("/api/v1/authorizations/{authorizationReference}", "AUTH-440001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.authorizationReference").value("AUTH-440001"))
                .andExpect(jsonPath("$.cardReference").value("CARD-440001"))
                .andExpect(jsonPath("$.merchantCode").value("MER-440001"))
                .andExpect(jsonPath("$.amount").value(125.75))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.authorizationType").value("PURCHASE"))
                .andExpect(jsonPath("$.channel").value("POS"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(authorizationService).getAuthorization("AUTH-440001");
    }

    @Test
    void getMissingAuthorizationReturnsNotFound() throws Exception {
        when(authorizationService.getAuthorization("AUTH-NOT-FOUND"))
                .thenThrow(new AuthorizationNotFoundException("AUTH-NOT-FOUND"));

        mockMvc.perform(get("/api/v1/authorizations/{authorizationReference}", "AUTH-NOT-FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Authorization not found with authorization reference: AUTH-NOT-FOUND"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/authorizations/AUTH-NOT-FOUND"));
    }

    private static AuthorizationCreateRequest validRequest() {
        return new AuthorizationCreateRequest(
                "AUTH-440001",
                "CARD-440001",
                "MER-440001",
                new BigDecimal("125.75"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.POS
        );
    }

    private static AuthorizationResponse response(AuthorizationStatus status) {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        return new AuthorizationResponse(
                1L,
                "AUTH-440001",
                "CARD-440001",
                "MER-440001",
                new BigDecimal("125.75"),
                "USD",
                AuthorizationType.PURCHASE,
                AuthorizationChannel.POS,
                status,
                now,
                now
        );
    }
}
