package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record FindDocument(UUID documentId, UUID ownerId) implements Query<Optional<DocumentDetailView>> {}
