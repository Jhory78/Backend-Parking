package com.parking.domain.entity;

import com.parking.domain.exception.OwnAccountDeactivationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static User userWithId(long id) {
        User user = User.create("Ana", "ana@parking.com", "hash", Role.OPERATOR);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void newAccountIsActiveWithNormalizedData() {
        User user = User.create("  Ana Pérez ", "  Ana@Parking.COM ", "hash", Role.OPERATOR);

        assertThat(user.getName()).isEqualTo("Ana Pérez");
        assertThat(user.getEmail()).isEqualTo("ana@parking.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void anotherAccountCanDeactivateIt() {
        User user = userWithId(2);

        user.deactivateBy(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void nobodyCanDeactivateTheirOwnAccount() {
        User user = userWithId(1);

        assertThatThrownBy(() -> user.deactivateBy(1L))
                .isInstanceOf(OwnAccountDeactivationException.class)
                .hasMessage("No puede desactivar su propia cuenta");
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void deactivatedAccountCanBeReactivated() {
        User user = userWithId(2);
        user.deactivateBy(1L);

        user.activate();

        assertThat(user.isActive()).isTrue();
    }
}
