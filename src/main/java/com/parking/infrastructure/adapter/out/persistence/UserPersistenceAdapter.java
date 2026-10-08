package com.parking.infrastructure.adapter.out.persistence;

import com.parking.application.port.out.UserRepositoryPort;
import com.parking.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository springDataRepository;

    @Override
    public List<User> findAll() {
        return springDataRepository.findAll();
    }

    @Override
    public Optional<User> findById(Long userId) {
        return springDataRepository.findById(userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springDataRepository.findByEmailIgnoreCase(email.trim());
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataRepository.existsByEmailIgnoreCase(email.trim());
    }

    @Override
    public User save(User user) {
        return springDataRepository.save(user);
    }
}
