package com.parking.application.port.out;

import com.parking.domain.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Account storage. Email lookups are case-insensitive.
 */
public interface UserRepositoryPort {

    List<User> findAll();

    Optional<User> findById(Long userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}
