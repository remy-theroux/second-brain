package xyz.sterenn.secondbrain.users.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

// Annotations JPA dans le domaine : écart assumé, voir ADR-0002.
@Entity
@Table(name = "users_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = Email.MAX_LENGTH)
    private Email email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {}

    private User(Email email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.verified = false;
    }

    public static User register(Email email, String passwordHash) {
        return new User(email, passwordHash);
    }

    public void verify() {
        this.verified = true;
    }

    public UUID getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
