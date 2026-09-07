package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.AgentRun;

/** Outbound port to the execution traces: they are written, and read back by owner. */
public interface AgentRunRepository {

    AgentRun save(AgentRun agentRun);

    List<AgentRun> findByOwnerId(UUID ownerId);
}
