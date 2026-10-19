package com.parking.infrastructure.adapter.in.rest.mapper;

import com.parking.application.port.in.CreateUserCommand;
import com.parking.application.port.in.UpdateUserCommand;
import com.parking.domain.entity.User;
import com.parking.infrastructure.adapter.in.rest.dto.CreateUserRequest;
import com.parking.infrastructure.adapter.in.rest.dto.UpdateUserRequest;
import com.parking.infrastructure.adapter.in.rest.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates between the HTTP contract and the application layer.
 */
@Component
public class UserRestMapper {

    public CreateUserCommand toCommand(CreateUserRequest request) {
        return new CreateUserCommand(request.name(), request.email(), request.password(), request.role());
    }

    public UpdateUserCommand toCommand(UpdateUserRequest request) {
        return new UpdateUserCommand(request.name(), request.email(), request.password());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus());
    }

    public List<UserResponse> toResponses(List<User> users) {
        return users.stream().map(this::toResponse).toList();
    }
}
