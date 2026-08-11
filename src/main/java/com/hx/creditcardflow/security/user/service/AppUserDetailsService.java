package com.hx.creditcardflow.security.user.service;

import com.hx.creditcardflow.security.user.entity.AppUser;
import com.hx.creditcardflow.security.user.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Application user not found: " + username
                ));

        return User.withUsername(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .authorities("ROLE_" + appUser.getRole().name())
                .disabled(!appUser.isEnabled())
                .build();
    }
}
