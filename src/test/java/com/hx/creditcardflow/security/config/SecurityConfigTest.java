package com.hx.creditcardflow.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void securityFoundationLoadsAndPasswordEncoderSecurelyMatchesPassword() {
        String rawPassword = "synthetic-test-password";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(securityFilterChain).isNotNull();
        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
    }

    @Test
    void existingBusinessApiRemainsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/merchants"))
                .andExpect(status().isOk());
    }
}
