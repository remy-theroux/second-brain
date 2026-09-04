package xyz.sterenn.secondbrain.users.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;

@Entity
@Table(name = "users_verification_tokens")
public class VerificationToken {

    public static final Duration VALIDITY = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationToken() {}

    private VerificationToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static VerificationToken issue(UUID userId, String tokenHash, Instant maintenant) {
        return new VerificationToken(userId, tokenHash, maintenant.plus(VALIDITY));
    }

    public boolean isExpired(Instant maintenant) {
        return maintenant.isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** L'usage unique ne tient qu'à ce lire-puis-écrire, sans verrou en base : voir ADR-0008. */
    public void consume(Instant maintenant) {
        if (isConsumed()) {
            throw new AlreadyUsedVerificationLinkException();
        }
        if (isExpired(maintenant)) {
            throw new ExpiredVerificationLinkException();
        }
        this.consumedAt = maintenant;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
