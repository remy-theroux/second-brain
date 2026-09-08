package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;

@Component
public class JpaDriveChannelRepositoryAdapter implements DriveChannelRepository {

    private final SpringDataDriveChannelRepository springDataDriveChannelRepository;

    JpaDriveChannelRepositoryAdapter(SpringDataDriveChannelRepository springDataDriveChannelRepository) {
        this.springDataDriveChannelRepository = springDataDriveChannelRepository;
    }

    @Override
    public DriveChannel save(DriveChannel driveChannel) {
        return springDataDriveChannelRepository.saveAndFlush(driveChannel);
    }

    @Override
    public Optional<DriveChannel> findByChannelId(String channelId) {
        return springDataDriveChannelRepository.findByChannelId(channelId);
    }

    @Override
    public Optional<DriveChannel> findByConnectionId(UUID connectionId) {
        return springDataDriveChannelRepository.findByConnectionId(connectionId);
    }

    @Override
    public void delete(DriveChannel driveChannel) {
        springDataDriveChannelRepository.delete(driveChannel);
        springDataDriveChannelRepository.flush();
    }
}
