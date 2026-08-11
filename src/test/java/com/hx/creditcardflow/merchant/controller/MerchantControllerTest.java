package com.hx.creditcardflow.merchant.controller;

import com.hx.creditcardflow.common.exception.GlobalExceptionHandler;
import com.hx.creditcardflow.merchant.dto.MerchantCreateRequest;
import com.hx.creditcardflow.merchant.dto.MerchantResponse;
import com.hx.creditcardflow.merchant.dto.MerchantUpdateRequest;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.exception.DuplicateMerchantCodeException;
import com.hx.creditcardflow.merchant.exception.MerchantNotFoundException;
import com.hx.creditcardflow.merchant.service.MerchantService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MerchantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private MerchantService merchantService;

    @Test
    void shouldCreateMerchant() throws Exception {
        MerchantCreateRequest request = createRequest("M300001");
        MerchantResponse response = response(
                1L,
                "M300001",
                "Aurora Garden Supply LLC",
                "Aurora Garden Supply",
                "5261",
                "US",
                MerchantStatus.ACTIVE
        );
        when(merchantService.createMerchant(request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantCode").value("M300001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(merchantService).createMerchant(request);
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        MerchantCreateRequest request = new MerchantCreateRequest(
                " ",
                "Aurora Garden Supply LLC",
                "Aurora Garden Supply",
                "52A",
                "us",
                MerchantStatus.ACTIVE
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.merchantCode").exists())
                .andExpect(jsonPath("$.validationErrors.merchantCategoryCode").exists())
                .andExpect(jsonPath("$.validationErrors.countryCode").exists());

        verify(merchantService, never()).createMerchant(any(MerchantCreateRequest.class));
    }

    @Test
    void shouldReturnConflictForDuplicateMerchantCode() throws Exception {
        MerchantCreateRequest request = createRequest("M300002");
        when(merchantService.createMerchant(request))
                .thenThrow(new DuplicateMerchantCodeException("M300002"));

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Merchant code already exists: M300002"));
    }

    @Test
    void shouldGetMerchant() throws Exception {
        MerchantResponse response = response(
                3L,
                "M300003",
                "Meadow Lane Pharmacy LLC",
                "Meadow Lane Pharmacy",
                "5912",
                "US",
                MerchantStatus.ACTIVE
        );
        when(merchantService.getMerchant(3L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/merchants/{id}", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.merchantCode").value("M300003"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnNotFoundForMissingMerchant() throws Exception {
        when(merchantService.getMerchant(404L)).thenThrow(new MerchantNotFoundException(404L));

        mockMvc.perform(get("/api/v1/merchants/{id}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/merchants/404"));
    }

    @Test
    void shouldGetAllMerchants() throws Exception {
        MerchantResponse first = response(
                4L,
                "M300004",
                "Blue Heron Books LLC",
                "Blue Heron Books",
                "5942",
                "US",
                MerchantStatus.ACTIVE
        );
        MerchantResponse second = response(
                5L,
                "M300005",
                "Juniper Trail Cafe LLC",
                "Juniper Trail Cafe",
                "5812",
                "US",
                MerchantStatus.SUSPENDED
        );
        when(merchantService.getAllMerchants()).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldUpdateMerchant() throws Exception {
        MerchantUpdateRequest request = new MerchantUpdateRequest(
                "Crescent Hill Outfitters LLC",
                "Crescent Hill Outfitters",
                "5941",
                "CA",
                MerchantStatus.SUSPENDED
        );
        MerchantResponse response = response(
                6L,
                "M300006",
                request.legalName(),
                request.displayName(),
                request.merchantCategoryCode(),
                request.countryCode(),
                request.status()
        );
        when(merchantService.updateMerchant(6L, request)).thenReturn(response);

        mockMvc.perform(put("/api/v1/merchants/{id}", 6L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Crescent Hill Outfitters LLC"))
                .andExpect(jsonPath("$.displayName").value("Crescent Hill Outfitters"))
                .andExpect(jsonPath("$.merchantCategoryCode").value("5941"))
                .andExpect(jsonPath("$.countryCode").value("CA"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(merchantService).updateMerchant(6L, request);
    }

    @Test
    void shouldDeleteMerchant() throws Exception {
        mockMvc.perform(delete("/api/v1/merchants/{id}", 7L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(merchantService).deleteMerchant(7L);
    }

    private static MerchantCreateRequest createRequest(String merchantCode) {
        return new MerchantCreateRequest(
                merchantCode,
                "Aurora Garden Supply LLC",
                "Aurora Garden Supply",
                "5261",
                "US",
                MerchantStatus.ACTIVE
        );
    }

    private static MerchantResponse response(
            Long id,
            String merchantCode,
            String legalName,
            String displayName,
            String merchantCategoryCode,
            String countryCode,
            MerchantStatus status
    ) {
        Instant now = Instant.parse("2026-08-06T12:00:00Z");
        return new MerchantResponse(
                id,
                merchantCode,
                legalName,
                displayName,
                merchantCategoryCode,
                countryCode,
                status,
                now,
                now
        );
    }
}
