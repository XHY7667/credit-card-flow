package com.hx.creditcardflow.security.user.service;

import com.hx.creditcardflow.security.user.entity.AppRole;
import com.hx.creditcardflow.security.user.entity.AppUser;
import com.hx.creditcardflow.security.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private AppUserDetailsService appUserDetailsService;

    @Test
    void adminMapsEncodedPasswordEnabledStateAndRoleAdmin() {
        AppUser appUser = new AppUser(
                "admin-user", "{bcrypt}encoded-admin", AppRole.ADMIN, true
        );
        when(appUserRepository.findByUsername("admin-user"))
                .thenReturn(Optional.of(appUser));

        UserDetails details = appUserDetailsService.loadUserByUsername("admin-user");

        assertThat(details.getUsername()).isEqualTo("admin-user");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}encoded-admin");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void userMapsRoleUserAndDisabledState() {
        AppUser appUser = new AppUser(
                "standard-user", "{bcrypt}encoded-user", AppRole.USER, false
        );
        when(appUserRepository.findByUsername("standard-user"))
                .thenReturn(Optional.of(appUser));

        UserDetails details = appUserDetailsService.loadUserByUsername("standard-user");

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void missingUsernameThrowsUsernameNotFoundException() {
        when(appUserRepository.findByUsername("missing-user"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appUserDetailsService.loadUserByUsername("missing-user"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Application user not found: missing-user");
    }
}
