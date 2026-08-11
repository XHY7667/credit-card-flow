package com.hx.creditcardflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "management.endpoints.web.exposure.include=health,info,metrics")
@AutoConfigureMockMvc(addFilters = false)
class ActuatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configuredActuatorEndpointsAreAvailableAndHealthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItems(
                        "creditcardflow.authorization.approved",
                        "creditcardflow.authorization.declined",
                        "creditcardflow.reversal.completed",
                        "creditcardflow.clearing.posted"
                )));

        mockMvc.perform(get(
                        "/actuator/metrics/creditcardflow.authorization.approved"))
                .andExpect(status().isOk());
    }
}
