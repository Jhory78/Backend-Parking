package com.parking.application.port.in;

import com.parking.domain.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Account queries.
 */
public interface FindUsersUseCase {

    List<User> findAllUsers();

    User findUserById(Long userId);

    /** Case-insensitive lookup by login identifier (RF-40). */
    Optional<User> findUserByEmail(String email);

    boolean existsUserWithEmail(String email);
}
