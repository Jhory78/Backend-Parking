package com.parking.infrastructure.utils.constants;

public final class SecurityConstants {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String TOKEN_TYPE = "Bearer";
    public static final String ROLE_PREFIX = "ROLE_";

    public static final String CLAIM_ID = "id";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLE = "role";

    /** Endpoints reachable without a token. */
    public static final String[] PUBLIC_ENDPOINTS = {
            ApiRoutes.AUTH + ApiRoutes.LOGIN,
            ApiRoutes.AUTH + ApiRoutes.LOGOUT,
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // Without this, a 404/405/500 is forwarded to /error unauthenticated
            // and reaches the client as 401, which logs the user out in the frontend.
            "/error"
    };

    private SecurityConstants() {
    }
}
