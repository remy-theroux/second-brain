package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveAuthorizationRequest;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveAuthorizationRequestRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;

@Component
public class JpaDriveAuthorizationRequestRepositoryAdapter implements DriveAuthorizationRequestRepository {

    private final SpringDataDriveAuthorizationRequestRepository springDataDriveAuthorizationRequestRepository;

    JpaDriveAuthorizationRequestRepositoryAdapter(
            SpringDataDriveAuthorizationRequestRepository springDataDriveAuthorizationRequestRepository) {
        this.springDataDriveAuthorizationRequestRepository = springDataDriveAuthorizationRequestRepository;
    }

    @Override
    public DriveAuthorizationRequest save(DriveAuthorizationRequest driveAuthorizationRequest) {
        return springDataDriveAuthorizationRequestRepository.saveAndFlush(driveAuthorizationRequest);
    }

    @Override
    public Optional<DriveAuthorizationRequest> findByState(DriveAuthorizationState state) {
        return springDataDriveAuthorizationRequestRepository.findByState(state);
    }
}
