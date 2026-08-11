package com.hx.creditcardflow.merchant.integration;

import com.hx.creditcardflow.merchant.dto.MerchantCreateRequest;
import com.hx.creditcardflow.merchant.dto.MerchantUpdateRequest;
import com.hx.creditcardflow.merchant.entity.Merchant;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class MerchantApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        merchantRepository.deleteAll();
    }

    @Test
    void shouldCompleteMerchantCrudLifecycle() throws Exception {
        MerchantCreateRequest createRequest = new MerchantCreateRequest(
                "M200001",
                "Blue Harbor Retail LLC",
                "Blue Harbor Market",
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        long merchantId = jsonMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asLong();

        assertThat(merchantRepository.findById(merchantId))
                .isPresent()
                .get()
                .extracting(Merchant::getMerchantCode)
                .isEqualTo("M200001");

        mockMvc.perform(get("/api/v1/merchants/{id}", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantCode").value("M200001"));

        MerchantUpdateRequest updateRequest = new MerchantUpdateRequest(
                "Blue Harbor Mercantile LLC",
                "Blue Harbor Mercantile",
                "5399",
                "CA",
                MerchantStatus.SUSPENDED
        );

        mockMvc.perform(put("/api/v1/merchants/{id}", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantCode").value("M200001"));

        Merchant updatedMerchant = merchantRepository.findById(merchantId).orElseThrow();
        assertThat(updatedMerchant.getMerchantCode()).isEqualTo("M200001");
        assertThat(updatedMerchant.getLegalName()).isEqualTo("Blue Harbor Mercantile LLC");
        assertThat(updatedMerchant.getDisplayName()).isEqualTo("Blue Harbor Mercantile");
        assertThat(updatedMerchant.getMerchantCategoryCode()).isEqualTo("5399");
        assertThat(updatedMerchant.getCountryCode()).isEqualTo("CA");
        assertThat(updatedMerchant.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);

        mockMvc.perform(delete("/api/v1/merchants/{id}", merchantId))
                .andExpect(status().isNoContent());

        assertThat(merchantRepository.existsById(merchantId)).isFalse();

        mockMvc.perform(get("/api/v1/merchants/{id}", merchantId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectDuplicateMerchantCode() throws Exception {
        MerchantCreateRequest firstRequest = new MerchantCreateRequest(
                "M200002",
                "Cedar Point Stationery LLC",
                "Cedar Point Stationery",
                "5943",
                "US",
                MerchantStatus.ACTIVE
        );
        MerchantCreateRequest duplicateRequest = new MerchantCreateRequest(
                "M200002",
                "Maple Grove Paper Goods LLC",
                "Maple Grove Paper Goods",
                "5943",
                "US",
                MerchantStatus.ACTIVE
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));

        assertThat(merchantRepository.findAll())
                .filteredOn(merchant -> merchant.getMerchantCode().equals("M200002"))
                .hasSize(1);
    }

    @Test
    void shouldRejectInvalidMerchantRequestWithoutSavingData() throws Exception {
        MerchantCreateRequest invalidRequest = new MerchantCreateRequest(
                " ",
                " ",
                " ",
                "54A",
                "usa",
                null
        );

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.merchantCode").exists())
                .andExpect(jsonPath("$.validationErrors.legalName").exists())
                .andExpect(jsonPath("$.validationErrors.displayName").exists())
                .andExpect(jsonPath("$.validationErrors.merchantCategoryCode").exists())
                .andExpect(jsonPath("$.validationErrors.countryCode").exists())
                .andExpect(jsonPath("$.validationErrors.status").exists());

        assertThat(merchantRepository.count()).isZero();
    }
}
