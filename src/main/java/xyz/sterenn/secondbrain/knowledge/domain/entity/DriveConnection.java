package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Entity
@Table(name = "knowledge_drive_connections")
public class DriveConnection {

    public static final int MAX_GOOGLE_EMAIL_LENGTH = 320;

    public static final int MAX_STATUS_LENGTH = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID ownerId;

    // A String and not the Email of the users context: a bounded context does not import
    // another one's value object.
    @Column(name = "google_email", nullable = false, length = MAX_GOOGLE_EMAIL_LENGTH)
    private String googleEmail;

    // columnDefinition: the ciphertext outgrows a varchar(255), and `ddl-auto: validate`
    // refuses the mismatch at startup.
    @Column(name = "refresh_token", nullable = false, columnDefinition = "text")
    private RefreshToken refreshToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = MAX_STATUS_LENGTH)
    private DriveConnectionStatus status;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    protected DriveConnection() {}

    private DriveConnection(UUID ownerId, String googleEmail, RefreshToken refreshToken, Instant connectedAt) {
        this.ownerId = ownerId;
        this.googleEmail = googleEmail;
        this.refreshToken = refreshToken;
        this.status = DriveConnectionStatus.ACTIVE;
        this.connectedAt = connectedAt;
    }

    public static DriveConnection connect(
            UUID ownerId, String googleEmail, RefreshToken refreshToken, Instant connectedAt) {
        if (ownerId == null) {
            throw new IllegalArgumentException("The connection owner is required");
        }
        return new DriveConnection(ownerId, requireGoogleEmail(googleEmail), requireToken(refreshToken), connectedAt);
    }

    /** Reconnecting replaces: one account holds one Drive connection, never a second. */
    public void refresh(String googleEmail, RefreshToken refreshToken, Instant connectedAt) {
        this.googleEmail = requireGoogleEmail(googleEmail);
        this.refreshToken = requireToken(refreshToken);
        this.status = DriveConnectionStatus.ACTIVE;
        this.connectedAt = connectedAt;
    }

    public void markNeedsReconnection() {
        this.status = DriveConnectionStatus.NEEDS_RECONNECTION;
    }

    private static String requireGoogleEmail(String googleEmail) {
        if (googleEmail == null || googleEmail.isBlank()) {
            throw new IllegalArgumentException("The Google address of the connected account is required");
        }
        String trimmed = googleEmail.trim();
        if (trimmed.length() > MAX_GOOGLE_EMAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "A Google address is at most " + MAX_GOOGLE_EMAIL_LENGTH + " characters");
        }
        return trimmed;
    }

    private static RefreshToken requireToken(RefreshToken refreshToken) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("A connection without a refresh token dies at the next restart");
        }
        return refreshToken;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    public DriveConnectionStatus getStatus() {
        return status;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }
}
