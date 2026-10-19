package com.parking.application.usecase;

import com.parking.application.port.in.CreateUserCommand;
import com.parking.application.port.in.UpdateUserCommand;
import com.parking.application.port.out.PasswordHasherPort;
import com.parking.domain.entity.Role;
import com.parking.domain.entity.User;
import com.parking.domain.entity.UserStatus;
import com.parking.domain.exception.EmailAlreadyInUseException;
import com.parking.domain.exception.OwnAccountDeactivationException;
import com.parking.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Use cases tested through their ports with in-memory adapters: no Spring, no database.
 */
class UserServiceTest {

    /** Reversible fake: makes it easy to assert which password was stored. */
    private static final PasswordHasherPort FAKE_HASHER = raw -> "hashed:" + raw;

    private InMemoryUserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        userService = new UserService(userRepository, FAKE_HASHER);
    }

    private User createOperator(String email) {
        return userService.createUser(new CreateUserCommand("Operator", email, "secret1", null));
    }

    @Test
    void createsOperatorByDefaultWithHashedPassword() {
        User user = createOperator("op@parking.com");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPassword()).isEqualTo("hashed:secret1");
    }

    @Test
    void rejectsDuplicatedEmailIgnoringCase() {
        createOperator("op@parking.com");

        assertThatThrownBy(() -> createOperator("OP@Parking.com"))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void blankPasswordKeepsTheCurrentOne() {
        User user = createOperator("op@parking.com");

        userService.updateUser(user.getId(), new UpdateUserCommand("New name", "op@parking.com", ""));

        assertThat(user.getName()).isEqualTo("New name");
        assertThat(user.getPassword()).isEqualTo("hashed:secret1");
    }

    @Test
    void newPasswordIsHashed() {
        User user = createOperator("op@parking.com");

        userService.updateUser(user.getId(), new UpdateUserCommand("Operator", "op@parking.com", "other99"));

        assertThat(user.getPassword()).isEqualTo("hashed:other99");
    }

    @Test
    void cannotTakeTheEmailOfAnotherAccount() {
        User first = createOperator("a@parking.com");
        createOperator("b@parking.com");

        assertThatThrownBy(() -> userService.updateUser(
                first.getId(), new UpdateUserCommand("A", "b@parking.com", null)))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void deactivatesAnotherAccountButNotItsOwn() {
        User admin = createOperator("admin@parking.com");
        User operator = createOperator("op@parking.com");

        userService.changeStatus(operator.getId(), UserStatus.INACTIVE, admin.getId());
        assertThat(operator.isActive()).isFalse();

        assertThatThrownBy(() -> userService.changeStatus(admin.getId(), UserStatus.INACTIVE, admin.getId()))
                .isInstanceOf(OwnAccountDeactivationException.class);
    }

    @Test
    void unknownAccountIsNotFound() {
        assertThatThrownBy(() -> userService.changeStatus(99L, UserStatus.ACTIVE, 1L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
