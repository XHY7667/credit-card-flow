package com.hx.creditcardflow.security.authentication.integration;

import com.hx.creditcardflow.security.authentication.dto.LoginRequest;
import com.hx.creditcardflow.security.authentication.service.AuthenticationService;
import com.hx.creditcardflow.security.config.SecurityConfig;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthenticationApiIntegrationTest {

    private static final String RAW_PASSWORD = "synthetic-test-password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        appUserRepository.deleteAll();
    }

    @Test
    void enabledUserCanLoginAndUseTheNativeBearerToken()
            throws Exception {
        persistUser("integration-admin", AppRole.ADMIN, true);

        MvcResult loginResult = performLogin("integration-admin", RAW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn")
                        .value(AuthenticationService.ACCESS_TOKEN_LIFETIME_SECONDS))
                .andReturn();

        JsonNode response = jsonMapper.readTree(
                loginResult.getResponse().getContentAsString()
        );
        String accessToken = response.get("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo("integration-admin");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(SecurityConfig.JWT_ISSUER);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());

        mockMvc.perform(get("/api/v1/merchants")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorizedResponse() throws Exception {
        persistUser("integration-user", AppRole.USER, true);

        performLogin("integration-user", "incorrect-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"));
    }

    @Test
    void unknownUsernameReturnsGenericUnauthorizedResponse() throws Exception {
        performLogin("missing-user", RAW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"));
    }

    @Test
    void disabledUserReturnsGenericUnauthorizedResponse() throws Exception {
        persistUser("disabled-user", AppRole.USER, false);

        performLogin("disabled-user", RAW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed"));
    }

    @Test
    void blankCredentialsFailBeanValidation() throws Exception {
        performLogin("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.username").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    private void persistUser(String username, AppRole role, boolean enabled) {
        appUserRepository.saveAndFlush(new AppUser(
                username,
                passwordEncoder.encode(RAW_PASSWORD),
                role,
                enabled
        ));
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(
            String username,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(username, password))));
    }
}
