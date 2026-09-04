package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.AgentRun;
import xyz.sterenn.secondbrain.knowledge.domain.port.AgentRunRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.CitedSource;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class RecordAgentRunHandler implements CommandHandler<RecordAgentRun> {

    private final AgentRunRepository agentRunRepository;
    private final Clock clock;

    public RecordAgentRunHandler(AgentRunRepository agentRunRepository, Clock clock) {
        this.agentRunRepository = agentRunRepository;
        this.clock = clock;
    }

    @Override
    public void handle(RecordAgentRun command) {
        agentRunRepository.save(AgentRun.of(
                command.ownerId(),
                command.agentName(),
                command.agentVersion(),
                command.question(),
                command.answer(),
                command.verdict(),
                command.turns(),
                command.durationMillis(),
                command.searches(),
                command.sources().stream().map(CitedSource::of).toList(),
                clock.instant()));
    }
}
