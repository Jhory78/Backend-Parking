package com.parking.infrastructure.config;

import com.parking.application.port.in.CreateUserCommand;
import com.parking.application.port.in.CreateUserUseCase;
import com.parking.application.port.in.FindUsersUseCase;
import com.parking.domain.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Guarantees there is always an administrator to log in with on a fresh database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final FindUsersUseCase findUsersUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final AdminAccountProperties adminAccount;

    @Override
    public void run(ApplicationArguments args) {
        if (findUsersUseCase.existsUserWithEmail(adminAccount.email())) {
            return;
        }

        createUserUseCase.createUser(new CreateUserCommand(
                adminAccount.name(), adminAccount.email(), adminAccount.password(), Role.ADMIN));
        log.info("Initial administrator account created: {}", adminAccount.email());
    }
}
