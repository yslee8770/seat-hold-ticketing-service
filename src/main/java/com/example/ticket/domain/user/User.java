package com.example.ticket.domain.user;

import com.example.ticket.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = {"email"})
        },
        indexes = {
                @Index(name = "ix_users_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role;

    private User(String email, String passwordHash, UserStatus status, UserRole role) {
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = Objects.requireNonNull(status);
        this.role = Objects.requireNonNull(role);
    }

    public static User createUser(String email, String passwordHash) {
        return new User(email, passwordHash, UserStatus.ACTIVE, UserRole.USER);
    }

    public static User createAdmin(String email, String passwordHash) {
        return new User(email, passwordHash, UserStatus.ACTIVE, UserRole.ADMIN);
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }
}
