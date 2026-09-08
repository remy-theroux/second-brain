package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record BrowseDriveFolders(UUID ownerId, String parentId) implements Query<List<DriveFolderView>> {}
