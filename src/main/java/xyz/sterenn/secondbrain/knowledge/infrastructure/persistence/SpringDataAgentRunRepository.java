package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.AgentRun;

interface SpringDataAgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
