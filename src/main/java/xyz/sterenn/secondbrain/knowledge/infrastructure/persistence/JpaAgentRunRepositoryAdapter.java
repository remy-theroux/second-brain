package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.AgentRun;
import xyz.sterenn.secondbrain.knowledge.domain.port.AgentRunRepository;

@Repository
class JpaAgentRunRepositoryAdapter implements AgentRunRepository {

    private final SpringDataAgentRunRepository springDataAgentRunRepository;

    JpaAgentRunRepositoryAdapter(SpringDataAgentRunRepository springDataAgentRunRepository) {
        this.springDataAgentRunRepository = springDataAgentRunRepository;
    }

    @Override
    public AgentRun save(AgentRun agentRun) {
        return springDataAgentRunRepository.save(agentRun);
    }

    @Override
    public List<AgentRun> findByOwnerId(UUID ownerId) {
        return springDataAgentRunRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }
}
