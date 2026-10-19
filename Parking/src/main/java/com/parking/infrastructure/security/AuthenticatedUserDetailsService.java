package com.parking.infrastructure.security;

import com.parking.application.port.in.FindUsersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads accounts by email, the login identifier (RF-40), through the
 * {@link FindUsersUseCase} input port.
 */
@Service
@RequiredArgsConstructor
public class AuthenticatedUserDetailsService implements UserDetailsService {

    private final FindUsersUseCase findUsersUseCase;

    @Override
    public AuthenticatedUser loadUserByUsername(String email) {
        return findUsersUseCase.findUserByEmail(email)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for email: " + email));
    }
}
