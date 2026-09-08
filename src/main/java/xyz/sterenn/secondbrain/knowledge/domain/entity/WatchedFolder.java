package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Entity
@Table(name = "knowledge_drive_watched_folders")
public class WatchedFolder {

    public static final int MAX_DRIVE_FOLDER_ID_LENGTH = 255;

    public static final int MAX_NAME_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "connection_id", nullable = false, columnDefinition = "uuid")
    private UUID connectionId;

    @Column(name = "drive_folder_id", nullable = false, length = MAX_DRIVE_FOLDER_ID_LENGTH)
    private String driveFolderId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;

    protected WatchedFolder() {}

    private WatchedFolder(UUID connectionId, String driveFolderId, String name, Instant watchedAt) {
        this.connectionId = connectionId;
        this.driveFolderId = driveFolderId;
        this.name = name;
        this.watchedAt = watchedAt;
    }

    public static WatchedFolder watch(UUID connectionId, DriveFolder folder, Instant watchedAt) {
        if (connectionId == null) {
            throw new IllegalArgumentException("A watched folder belongs to a Drive connection");
        }
        if (folder == null) {
            throw new IllegalArgumentException("The folder to watch is required");
        }
        if (watchedAt == null) {
            throw new IllegalArgumentException("The instant a folder was put under watch is required");
        }
        return new WatchedFolder(connectionId, requireDriveFolderId(folder.id()), bounded(folder.name()), watchedAt);
    }

    private static String requireDriveFolderId(String driveFolderId) {
        if (driveFolderId.length() > MAX_DRIVE_FOLDER_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "A Drive folder identifier is at most " + MAX_DRIVE_FOLDER_ID_LENGTH + " characters");
        }
        return driveFolderId;
    }

    /** Truncated rather than refused, as a filename is: the name is a label, not an identity. */
    private static String bounded(String name) {
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public String getName() {
        return name;
    }

    public Instant getWatchedAt() {
        return watchedAt;
    }
}
