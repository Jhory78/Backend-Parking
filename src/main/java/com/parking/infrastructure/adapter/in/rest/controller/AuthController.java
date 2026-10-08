package com.parking.infrastructure.adapter.in.rest.controller;

import com.parking.application.port.in.LoginUseCase;
import com.parking.infrastructure.adapter.in.rest.dto.LoginRequest;
import com.parking.infrastructure.adapter.in.rest.dto.LoginResponse;
import com.parking.infrastructure.adapter.in.rest.dto.UserResponse;
import com.parking.infrastructure.adapter.in.rest.mapper.UserRestMapper;
import com.parking.infrastructure.security.AuthenticatedUser;
import com.parking.infrastructure.utils.constants.ApiRoutes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.AUTH)
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final UserRestMapper userMapper;

    /** RF-40, RF-41 */
    @PostMapping(ApiRoutes.LOGIN)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(loginUseCase.login(request.email(), request.password()));
    }

    /** Profile of the logged-in account; the frontend uses it to confirm the role (RF-42). */
    @GetMapping(ApiRoutes.CURRENT_USER)
    public UserResponse getCurrentUser(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userMapper.toResponse(currentUser.getUser());
    }

    /** Tokens are stateless: logging out only requires the client to discard the token. */
    @PostMapping(ApiRoutes.LOGOUT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Intentionally empty
    }
}
