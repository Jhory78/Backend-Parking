package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.AccessToken;

import static com.parking.infrastructure.utils.constants.SecurityConstants.TOKEN_TYPE;

/**
 * @param expiresIn token lifetime in seconds.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static LoginResponse from(AccessToken token) {
        return new LoginResponse(token.value(), TOKEN_TYPE, token.expiresIn().toSeconds());
    }
}
