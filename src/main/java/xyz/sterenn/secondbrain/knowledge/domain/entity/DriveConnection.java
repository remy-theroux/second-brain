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
import java.util.Optional;
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

    // columnDefinition: Google documents no length for this token, and a silent truncation would
    // hand back a token Google refuses, hence a full scan.
    @Column(name = "changes_page_token", columnDefinition = "text")
    private String changesPageToken;

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

    /**
     * Reconnecting replaces: one account holds one Drive connection, never a second. The change
     * position goes with the Drive that handed it out — kept across a reconnection on another
     * Google account, it is one Google refuses in 400 rather than in 410, so no round would ever
     * fall back on the full scan that a dead position is owed.
     */
    public void refresh(String googleEmail, RefreshToken refreshToken, Instant connectedAt) {
        this.googleEmail = requireGoogleEmail(googleEmail);
        this.refreshToken = requireToken(refreshToken);
        this.status = DriveConnectionStatus.ACTIVE;
        this.connectedAt = connectedAt;
        this.changesPageToken = null;
    }

    public void markNeedsReconnection() {
        this.status = DriveConnectionStatus.NEEDS_RECONNECTION;
    }

    /** Only after a whole run went through: kept page by page, it would lose a failed page's changes. */
    public void keepChangesPageToken(String changesPageToken) {
        if (changesPageToken == null || changesPageToken.isBlank()) {
            throw new IllegalArgumentException("A change feed without its page token cannot be resumed");
        }
        this.changesPageToken = changesPageToken.trim();
    }

    /**
     * What is owed after a token Google no longer knows: a full scan, so the next run asks for a
     * starting point again rather than resuming from a dead one.
     */
    public void forgetChangesPageToken() {
        this.changesPageToken = null;
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

    /** Empty until a run went through: nothing tells where a connection that never read stands. */
    public Optional<String> getChangesPageToken() {
        return Optional.ofNullable(changesPageToken);
    }
}
