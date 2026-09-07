package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.GroundingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.PromptBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Absorption;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Answer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

/**
 * Neither a command nor a query, and the controller calls it directly: a conversation lasts
 * minutes, and the transaction a bus opens would hold a PostgreSQL connection all that time.
 * Database access stays behind the buses — one short transaction per search.
 */
@Component
public class ConversationAgent {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationAgent.class);

    private record ToolOutcome(String text, SourceCatalogue catalogue, Optional<String> query) {}

    private final Agent agent;
    private final LlmPort llmPort;
    private final DocumentSearchTool documentSearchTool;
    private final Clock clock;

    public ConversationAgent(Agent agent, LlmPort llmPort, DocumentSearchTool documentSearchTool, Clock clock) {
        this.agent = agent;
        this.llmPort = llmPort;
        this.documentSearchTool = documentSearchTool;
        this.clock = clock;
    }

    public Question validate(String question) {
        return new Question(question);
    }

    public String agentName() {
        return agent.name();
    }

    public String agentVersion() {
        return agent.version();
    }

    /** On return, the text of the {@code Answer} has already been emitted by {@code onToken}: do not re-emit it. */
    public ConversationOutcome answer(Question question, UUID ownerId, Consumer<String> onToken) {
        Instant start = clock.instant();
        List<LlmMessage> messages =
                new ArrayList<>(List.of(PromptBuilder.systemMessage(agent), LlmMessage.user(question.value())));
        SourceCatalogue catalogue = SourceCatalogue.empty();
        List<String> searches = new ArrayList<>();
        int turns = 0;

        while (turns < agent.budget().maxTurns() && !budgetExhausted(start)) {
            turns++;
            boolean searchPerformed = !searches.isEmpty();
            CitationBuffer buffer = new CitationBuffer(catalogue, onToken);
            LlmTurn turn = llmPort.stream(new LlmRequest(messages, agent.tools(), agent.temperature()), buffer::accept);

            if (!turn.requestsATool()) {
                if (buffer.text().isBlank()) {
                    LOG.warn("Le modèle a rendu un tour vide (tour {}) ; tour consommé sans relance.", turns);
                    continue;
                }
                Answer answer = GroundingPolicy.verdict(agent, buffer.text(), catalogue, searchPerformed);
                if (!buffer.isOpen()) {
                    onToken.accept(answer.text());
                }
                return new ConversationOutcome(answer, searches, turns, elapsed(start));
            }

            messages.add(LlmMessage.toolRequest(turn.toolCalls()));
            for (ToolCall call : turn.toolCalls()) {
                ToolOutcome outcome = execute(call, ownerId, catalogue);
                catalogue = outcome.catalogue();
                outcome.query().ifPresent(searches::add);
                messages.add(LlmMessage.toolResult(call.id(), outcome.text()));
            }
        }

        Answer answer = GroundingPolicy.budgetExceeded(agent);
        onToken.accept(answer.text());
        return new ConversationOutcome(answer, searches, turns, elapsed(start));
    }

    private ToolOutcome execute(ToolCall call, UUID ownerId, SourceCatalogue catalogue) {
        if (!agent.knows(call.name())) {
            String known = agent.tools().stream().map(ToolSpecification::name).collect(Collectors.joining(", "));
            return new ToolOutcome(
                    "L'outil « " + call.name() + " » n'existe pas. Outils disponibles : " + known + ".",
                    catalogue,
                    Optional.empty());
        }
        String query = call.argument(DocumentAgent.QUESTION_PARAMETER);
        if (query == null || query.isBlank()) {
            return new ToolOutcome(
                    "L'appel est incomplet : le paramètre « " + DocumentAgent.QUESTION_PARAMETER
                            + " » est obligatoire.",
                    catalogue,
                    Optional.empty());
        }
        List<SourceCandidate> candidates;
        try {
            candidates = documentSearchTool.search(query, ownerId);
        } catch (InvalidQuestionException refusal) {
            return new ToolOutcome("La recherche a été refusée : " + refusal.getMessage(), catalogue, Optional.empty());
        }
        Absorption absorption = catalogue.absorb(candidates);
        return new ToolOutcome(PromptBuilder.searchResult(absorption), absorption.catalogue(), Optional.of(query));
    }

    private boolean budgetExhausted(Instant start) {
        return elapsed(start).compareTo(agent.budget().limit()) >= 0;
    }

    private Duration elapsed(Instant start) {
        return Duration.between(start, clock.instant());
    }
}
