package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;

@Component
public class JpaDriveConnectionRepositoryAdapter implements DriveConnectionRepository {

    private final SpringDataDriveConnectionRepository springDataDriveConnectionRepository;

    JpaDriveConnectionRepositoryAdapter(SpringDataDriveConnectionRepository springDataDriveConnectionRepository) {
        this.springDataDriveConnectionRepository = springDataDriveConnectionRepository;
    }

    @Override
    public DriveConnection save(DriveConnection driveConnection) {
        // Without an explicit flush, the foreign key violation towards the owner and the
        // uniqueness of owner_id would only surface at commit.
        return springDataDriveConnectionRepository.saveAndFlush(driveConnection);
    }

    @Override
    public Optional<DriveConnection> findByOwnerId(UUID ownerId) {
        return springDataDriveConnectionRepository.findByOwnerId(ownerId);
    }

    @Override
    public void delete(DriveConnection driveConnection) {
        springDataDriveConnectionRepository.delete(driveConnection);
        springDataDriveConnectionRepository.flush();
    }
}
