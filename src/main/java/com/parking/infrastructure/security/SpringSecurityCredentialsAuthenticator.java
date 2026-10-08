package com.parking.infrastructure.security;

import com.parking.application.port.out.CredentialsAuthenticatorPort;
import com.parking.domain.entity.User;
import com.parking.domain.exception.AccountDisabledException;
import com.parking.domain.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Validates credentials with Spring Security and translates its exceptions
 * into the auth domain ones.
 */
@Component
@RequiredArgsConstructor
public class SpringSecurityCredentialsAuthenticator implements CredentialsAuthenticatorPort {

    private final AuthenticationManager authenticationManager;

    @Override
    public User authenticate(String email, String password) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            return ((AuthenticatedUser) authentication.getPrincipal()).getUser();
        } catch (BadCredentialsException ex) {
            // Also covers unknown emails: Spring Security hides UsernameNotFoundException
            throw new InvalidCredentialsException();
        } catch (DisabledException ex) {
            throw new AccountDisabledException();
        }
    }
}
