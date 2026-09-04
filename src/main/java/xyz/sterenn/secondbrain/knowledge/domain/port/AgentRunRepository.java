package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.AgentRun;

/** Port sortant vers les traces d'exécution : elles s'écrivent, et se relisent par propriétaire. */
public interface AgentRunRepository {

    AgentRun save(AgentRun agentRun);

    List<AgentRun> findByOwnerId(UUID ownerId);
}
