package com.parking.application.usecase;

import com.parking.domain.entity.AccessToken;
import com.parking.domain.entity.Role;
import com.parking.domain.entity.User;
import com.parking.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Login use case with fake adapters: neither Spring Security nor JWT are needed.
 */
class AuthServiceTest {

    private static final User ANA = User.create("Ana", "ana@parking.com", "hash", Role.OPERATOR);

    private final AuthService authService = new AuthService(
            (email, password) -> {
                if (email.equals("ana@parking.com") && password.equals("secret1")) {
                    return ANA;
                }
                throw new InvalidCredentialsException();
            },
            user -> new AccessToken("token-for-" + user.getEmail(), Duration.ofHours(1)));

    @Test
    void issuesATokenForValidCredentials() {
        AccessToken token = authService.login("  ana@parking.com ", "secret1");

        assertThat(token.value()).isEqualTo("token-for-ana@parking.com");
        assertThat(token.expiresIn()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void propagatesInvalidCredentials() {
        assertThatThrownBy(() -> authService.login("ana@parking.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }
}
