package com.parking.application.usecase;

import com.parking.application.port.out.UserRepositoryPort;
import com.parking.domain.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test adapter for the {@link UserRepositoryPort} port: lets the use cases be
 * tested without Spring or a database.
 */
class InMemoryUserRepository implements UserRepositoryPort {

    private final Map<Long, User> users = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    @Override
    public Optional<User> findById(Long userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(email.trim()))
                .findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            ReflectionTestUtils.setField(user, "id", sequence.incrementAndGet());
        }
        users.put(user.getId(), user);
        return user;
    }
}
