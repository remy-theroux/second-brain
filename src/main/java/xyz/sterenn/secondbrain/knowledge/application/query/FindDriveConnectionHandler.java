package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class FindDriveConnectionHandler implements QueryHandler<FindDriveConnection, Optional<DriveConnectionView>> {

    private final DriveConnectionRepository driveConnectionRepository;

    public FindDriveConnectionHandler(DriveConnectionRepository driveConnectionRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
    }

    @Override
    public Optional<DriveConnectionView> handle(FindDriveConnection query) {
        return driveConnectionRepository.findByOwnerId(query.ownerId()).map(DriveConnectionView::of);
    }
}
