package com.parking.infrastructure.adapter.in.rest.controller;

import com.parking.application.port.in.ChangeUserStatusUseCase;
import com.parking.application.port.in.CreateUserUseCase;
import com.parking.application.port.in.FindUsersUseCase;
import com.parking.application.port.in.UpdateUserUseCase;
import com.parking.infrastructure.adapter.in.rest.dto.CreateUserRequest;
import com.parking.infrastructure.adapter.in.rest.dto.UpdateUserRequest;
import com.parking.infrastructure.adapter.in.rest.dto.UpdateUserStatusRequest;
import com.parking.infrastructure.adapter.in.rest.dto.UserResponse;
import com.parking.infrastructure.adapter.in.rest.mapper.UserRestMapper;
import com.parking.infrastructure.utils.constants.ApiRoutes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account management. Only administrators can use it (RF-42).
 */
@RestController
@RequestMapping(ApiRoutes.USERS)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final ChangeUserStatusUseCase changeUserStatusUseCase;
    private final FindUsersUseCase findUsersUseCase;
    private final UserRestMapper mapper;

    /** RF-43 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return mapper.toResponse(createUserUseCase.createUser(mapper.toCommand(request)));
    }

    @GetMapping
    public List<UserResponse> findAllUsers() {
        return mapper.toResponses(findUsersUseCase.findAllUsers());
    }

    @GetMapping(ApiRoutes.USER_BY_ID)
    public UserResponse findUserById(@PathVariable Long userId) {
        return mapper.toResponse(findUsersUseCase.findUserById(userId));
    }

    /** RF-44 */
    @PutMapping(ApiRoutes.USER_BY_ID)
    public UserResponse updateUser(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
        return mapper.toResponse(updateUserUseCase.updateUser(userId, mapper.toCommand(request)));
    }

    /** RF-45. {@code currentUserId} comes from the token: nobody can deactivate themselves. */
    @PatchMapping(ApiRoutes.USER_STATUS)
    public UserResponse changeStatus(@PathVariable Long userId,
                                     @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal(expression = "id") Long currentUserId) {
        return mapper.toResponse(changeUserStatusUseCase.changeStatus(userId, request.status(), currentUserId));
    }
}
