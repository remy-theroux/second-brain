package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;

interface SpringDataDriveChannelRepository extends JpaRepository<DriveChannel, UUID> {

    Optional<DriveChannel> findByChannelId(String channelId);

    Optional<DriveChannel> findByConnectionId(UUID connectionId);
}
