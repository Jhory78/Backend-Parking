package com.parking.application.usecase;

import com.parking.application.port.in.LoginUseCase;
import com.parking.application.port.out.AccessTokenIssuerPort;
import com.parking.application.port.out.CredentialsAuthenticatorPort;
import com.parking.domain.entity.AccessToken;
import com.parking.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Login with email and password (RF-40). Credentials and account status are
 * validated by the {@link CredentialsAuthenticatorPort} (RF-41). This class does
 * not know Spring Security nor JWT.
 */
@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase {

    private final CredentialsAuthenticatorPort credentialsAuthenticator;
    private final AccessTokenIssuerPort accessTokenIssuer;

    @Override
    public AccessToken login(String email, String password) {
        User user = credentialsAuthenticator.authenticate(email.trim(), password);
        return accessTokenIssuer.issueFor(user);
    }
}
