package xyz.sterenn.secondbrain.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Compte utilisateur.
 *
 * <p>Le constructeur est privé : un compte ne se crée que par {@link #register}, ce qui
 * garantit l'invariant « un compte naît non vérifié ». {@code passwordHash} ne contient
 * jamais le mot de passe en clair.
 *
 * <p>Les annotations JPA dans le domaine sont un écart assumé au profit du minimalisme
 * (pas de classe miroir ni de mapper) — voir le plan d'architecture.
 */
@Entity
@Table(name = "users_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    // length explicite : Hibernate tourne en ddl-auto=validate et compare les
    // métadonnées de l'entité au schéma créé par Flyway.
    @Convert(converter = EmailAttributeConverter.class)
    @Column(nullable = false, unique = true, length = Email.MAX_LENGTH)
    private Email email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // requis par JPA
    }

    private User(Email email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.verified = false;
    }

    /**
     * Crée un compte nouvellement inscrit, dans l'état non vérifié.
     */
    public static User register(Email email, String passwordHash) {
        return new User(email, passwordHash);
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
