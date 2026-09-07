package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record RecordAgentRun(
        UUID ownerId,
        String agentName,
        String agentVersion,
        String question,
        String answer,
        AnswerVerdict verdict,
        int turns,
        long durationMillis,
        List<String> searches,
        List<Source> sources)
        implements Command {}
