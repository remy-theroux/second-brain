package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.DriveChannelPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelSubscription;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;

/**
 * A push subscription on one Drive. See ADR-0002 for the JPA annotations in the domain.
 *
 * <p>It is born in two steps because Google's protocol is in two steps: we choose the identifier
 * and the token, Google answers the resource and the deadline. Between {@code open} and
 * {@code subscribed} the channel authenticates nothing, and it is never handed to a repository.
 */
@Entity
@Table(name = "knowledge_drive_channels")
public class DriveChannel {

    public static final int MAX_CHANNEL_ID_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "connection_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID connectionId;

    @Column(name = "channel_id", nullable = false, unique = true, length = MAX_CHANNEL_ID_LENGTH)
    private String channelId;

    // columnDefinition: Google documents no length for this identifier, and a silent truncation
    // would hand back a channel that can never be stopped.
    @Column(name = "resource_id", columnDefinition = "text")
    private String resourceId;

    // columnDefinition: the ciphertext outgrows a varchar(255), same as the refresh token.
    @Column(nullable = false, columnDefinition = "text")
    private DriveChannelToken token;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected DriveChannel() {}

    private DriveChannel(UUID connectionId, String channelId, DriveChannelToken token, Instant openedAt) {
        this.connectionId = connectionId;
        this.channelId = channelId;
        this.token = token;
        this.openedAt = openedAt;
    }

    /**
     * Both secrets are drawn here rather than by the caller: nothing outside gets to choose the
     * identifier Google will echo back, nor the token that alone authenticates a notification.
     */
    public static DriveChannel open(UUID connectionId, Instant openedAt) {
        if (connectionId == null) {
            throw new IllegalArgumentException("The connection the channel watches is required");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("The instant the channel was opened is required");
        }
        return new DriveChannel(connectionId, UUID.randomUUID().toString(), DriveChannelToken.random(), openedAt);
    }

    public void subscribed(String resourceId, Instant expiresAt) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("A channel without its resource identifier can no longer be stopped");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("The deadline Google handed back is required");
        }
        this.resourceId = resourceId.trim();
        this.expiresAt = expiresAt;
    }

    public void subscribed(DriveChannelSubscription subscription) {
        subscribed(subscription.resourceId(), subscription.expiresAt());
    }

    /** An expired channel authenticates nothing: the token it carries proves the past, not the present. */
    public boolean accepts(DriveChannelToken candidate, Instant now) {
        return expiresAt != null && now.isBefore(expiresAt) && token.matches(candidate);
    }

    public boolean needsRenewal(Instant now) {
        return expiresAt == null || DriveChannelPolicy.needsRenewal(openedAt, expiresAt, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public DriveChannelToken getToken() {
        return token;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
