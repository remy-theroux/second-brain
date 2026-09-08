package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidDriveAuthorizationException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;

@Component
public class JpaDriveConnectionRepositoryAdapter implements DriveConnectionRepository {

    private final SpringDataDriveConnectionRepository springDataDriveConnectionRepository;

    JpaDriveConnectionRepositoryAdapter(SpringDataDriveConnectionRepository springDataDriveConnectionRepository) {
        this.springDataDriveConnectionRepository = springDataDriveConnectionRepository;
    }

    @Override
    public DriveConnection save(DriveConnection driveConnection) {
        try {
            // Without an explicit flush, the foreign key violation towards the owner and the
            // uniqueness of owner_id would only surface at commit, out of reach of this catch.
            return springDataDriveConnectionRepository.saveAndFlush(driveConnection);
        } catch (DataIntegrityViolationException violation) {
            // An authorization whose connection can no longer be written is no longer usable:
            // the callback redirects on `lien-invalide` rather than on a 500.
            throw new InvalidDriveAuthorizationException();
        }
    }

    @Override
    public Optional<DriveConnection> findByOwnerId(UUID ownerId) {
        return springDataDriveConnectionRepository.findByOwnerId(ownerId);
    }

    @Override
    public List<DriveConnection> findAllActive() {
        return springDataDriveConnectionRepository.findAllByStatus(DriveConnectionStatus.ACTIVE);
    }

    @Override
    public void delete(DriveConnection driveConnection) {
        springDataDriveConnectionRepository.delete(driveConnection);
        springDataDriveConnectionRepository.flush();
    }
}
