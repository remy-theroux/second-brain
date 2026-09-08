package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;

/** One connection holds at most one channel; a notification names it by the identifier we drew. */
public interface DriveChannelRepository {

    DriveChannel save(DriveChannel driveChannel);

    Optional<DriveChannel> findByChannelId(String channelId);

    Optional<DriveChannel> findByConnectionId(UUID connectionId);

    void delete(DriveChannel driveChannel);
}
