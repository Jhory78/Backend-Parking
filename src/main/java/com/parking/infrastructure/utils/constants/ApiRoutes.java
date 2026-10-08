package com.parking.infrastructure.utils.constants;

/**
 * REST routes, relative to the context path ({@code /pk/api}).
 */
public final class ApiRoutes {

    public static final String AUTH = "/auth";
    public static final String LOGIN = "/login";
    public static final String LOGOUT = "/logout";
    public static final String CURRENT_USER = "/me";

    public static final String USERS = "/users";
    public static final String USER_BY_ID = "/{userId}";
    public static final String USER_STATUS = "/{userId}/status";

    public static final String SPACES = "/spaces";
    public static final String SPACE_STATUS = "/{spaceId}/status";
    public static final String SPACES_AVAILABILITY = "/availability";
    public static final String SPACES_OCCUPANCY = "/occupancy";
    public static final String SPACES_STREAM = "/stream";

    private ApiRoutes() {
    }
}
