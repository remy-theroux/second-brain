package xyz.sterenn.secondbrain.knowledge.application.query;

import java.time.Instant;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;

/** Never the refresh token: no route hands it back. */
public record DriveConnectionView(String googleEmail, DriveConnectionStatus status, Instant connectedAt) {

    public static DriveConnectionView of(DriveConnection driveConnection) {
        return new DriveConnectionView(
                driveConnection.getGoogleEmail(), driveConnection.getStatus(), driveConnection.getConnectedAt());
    }
}
