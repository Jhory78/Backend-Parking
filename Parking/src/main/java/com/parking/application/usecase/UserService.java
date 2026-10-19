package com.parking.application.usecase;

import com.parking.application.port.in.ChangeUserStatusUseCase;
import com.parking.application.port.in.CreateUserCommand;
import com.parking.application.port.in.CreateUserUseCase;
import com.parking.application.port.in.FindUsersUseCase;
import com.parking.application.port.in.UpdateUserCommand;
import com.parking.application.port.in.UpdateUserUseCase;
import com.parking.application.port.out.PasswordHasherPort;
import com.parking.application.port.out.UserRepositoryPort;
import com.parking.domain.entity.User;
import com.parking.domain.entity.UserStatus;
import com.parking.domain.exception.EmailAlreadyInUseException;
import com.parking.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Account use cases (RF-43, RF-44, RF-45).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements CreateUserUseCase, UpdateUserUseCase, ChangeUserStatusUseCase, FindUsersUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;

    @Override
    public User createUser(CreateUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new EmailAlreadyInUseException();
        }

        User user = User.create(
                command.name(),
                command.email(),
                passwordHasher.hash(command.password()),
                command.roleOrDefault());

        return userRepository.save(user);
    }

    @Override
    public User updateUser(Long userId, UpdateUserCommand command) {
        User user = getUserOrThrow(userId);
        ensureEmailIsAvailable(command.email(), userId);

        user.updateProfile(command.name(), command.email());
        if (command.hasNewPassword()) {
            user.changePassword(passwordHasher.hash(command.newPassword()));
        }

        return userRepository.save(user);
    }

    @Override
    public User changeStatus(Long userId, UserStatus newStatus, Long actorId) {
        User user = getUserOrThrow(userId);

        if (newStatus == UserStatus.ACTIVE) {
            user.activate();
        } else {
            user.deactivateBy(actorId);
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserById(Long userId) {
        return getUserOrThrow(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsUserWithEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private void ensureEmailIsAvailable(String email, Long ownerId) {
        userRepository.findByEmail(email)
                .filter(existingUser -> !existingUser.getId().equals(ownerId))
                .ifPresent(existingUser -> {
                    throw new EmailAlreadyInUseException();
                });
    }
}
