package com.parking.infrastructure.security;

import com.parking.application.port.out.PasswordHasherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Uses the same {@link PasswordEncoder} (BCrypt) that Spring Security uses to
 * verify passwords on login.
 */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHasher implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
