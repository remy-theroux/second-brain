package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record SearchChunks(String question, UUID ownerId) implements Query<List<ChunkMatchView>> {}
