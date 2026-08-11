package com.hx.creditcardflow.security.authorization.integration;

import com.hx.creditcardflow.merchant.dto.MerchantCreateRequest;
import com.hx.creditcardflow.merchant.entity.MerchantStatus;
import com.hx.creditcardflow.merchant.repository.MerchantRepository;
import com.hx.creditcardflow.security.authentication.dto.LoginRequest;
import com.hx.creditcardflow.security.user.entity.AppRole;
import com.hx.creditcardflow.security.user.entity.AppUser;
import com.hx.creditcardflow.security.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthorizationPolicyIntegrationTest {

    private static final String RAW_PASSWORD = "synthetic-authorization-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository appUserRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        merchantRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void loginAndHealthArePublicWhileProtectedBusinessAndMetricsRoutesRequireJwt()
            throws Exception {
        persistUser("public-check-user", AppRole.USER);

        login("public-check-user");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/merchants")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userJwtCanReadBusinessDataButCannotWriteMasterDataOrReadMetrics()
            throws Exception {
        persistUser("application-user", AppRole.USER);
        String token = login("application-user");

        mockMvc.perform(get("/api/v1/merchants")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(merchantRequest())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminJwtCanWriteMasterDataAndReadMetrics() throws Exception {
        persistUser("application-admin", AppRole.ADMIN);
        String token = login("application-admin");

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(merchantRequest())))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private void persistUser(String username, AppRole role) {
        appUserRepository.saveAndFlush(new AppUser(
                username,
                passwordEncoder.encode(RAW_PASSWORD),
                role,
                true
        ));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new LoginRequest(username, RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private MerchantCreateRequest merchantRequest() {
        return new MerchantCreateRequest(
                "M-B4-001",
                "Security Integration Merchant LLC",
                "Security Integration Merchant",
                "5411",
                "US",
                MerchantStatus.ACTIVE
        );
    }
}
