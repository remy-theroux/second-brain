package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.DriveAuthorizationPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidDriveAuthorizationException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Entity
@Table(name = "knowledge_drive_authorization_requests")
public class DriveAuthorizationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(nullable = false, unique = true, length = DriveAuthorizationState.MAX_LENGTH)
    private DriveAuthorizationState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected DriveAuthorizationRequest() {}

    private DriveAuthorizationRequest(UUID ownerId, DriveAuthorizationState state, Instant createdAt) {
        this.ownerId = ownerId;
        this.state = state;
        this.createdAt = createdAt;
    }

    /**
     * The nonce is drawn by the caller and not here: a command returns nothing, so the route
     * would have no way of building the consent URL without re-reading what it just wrote.
     */
    public static DriveAuthorizationRequest open(UUID ownerId, DriveAuthorizationState state, Instant now) {
        if (ownerId == null) {
            throw new IllegalArgumentException("The requester of the authorization is required");
        }
        if (state == null) {
            throw new IllegalArgumentException("The authorization state is required");
        }
        return new DriveAuthorizationRequest(ownerId, state, now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    /**
     * Single use rests on this read-then-write alone, with no database lock: same dispositif as
     * the verification token, see ADR-0008.
     */
    public void consume(DriveAuthorizationState state, Instant now) {
        if (!this.state.equals(state) || isConsumed() || DriveAuthorizationPolicy.isExpired(createdAt, now)) {
            throw new InvalidDriveAuthorizationException();
        }
        this.consumedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public DriveAuthorizationState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
