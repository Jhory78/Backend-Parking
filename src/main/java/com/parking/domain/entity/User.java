package com.parking.domain.entity;

import com.parking.domain.exception.OwnAccountDeactivationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * System account (administrator or operator).
 * <p>
 * Accounts are never deleted: they are deactivated to keep the
 * traceability of the operations they performed (RF-45).
 * JPA annotations are allowed in the domain (see docs/ARCHITECTURE.md).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    private User(String name, String email, String passwordHash, Role role) {
        updateProfile(name, email);
        this.password = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    /** Creates a new active account. The password must already be hashed. */
    public static User create(String name, String email, String passwordHash, Role role) {
        return new User(name, email, passwordHash, role);
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void updateProfile(String name, String email) {
        this.name = name.trim();
        this.email = normalizeEmail(email);
    }

    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    /**
     * @param actorId account performing the change: nobody can deactivate their
     *                own account, so there is always an administrator able to log in.
     */
    public void deactivateBy(Long actorId) {
        if (id != null && id.equals(actorId)) {
            throw new OwnAccountDeactivationException();
        }
        this.status = UserStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
