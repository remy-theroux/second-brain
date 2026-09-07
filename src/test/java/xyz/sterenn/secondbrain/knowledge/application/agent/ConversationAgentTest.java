package xyz.sterenn.secondbrain.knowledge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.application.query.ChunkMatchView;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.TestAgents;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;
import xyz.sterenn.secondbrain.shared.bus.Query;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;

class ConversationAgentTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID REPORT = UUID.randomUUID();

    private final TestClock clock = new TestClock();
    private final ScriptedLlmPort llmPort = new ScriptedLlmPort();
    private final List<String> emitted = new ArrayList<>();

    private ConversationAgent agentWith(List<ChunkMatchView> searchResults) {
        QueryBus queryBus = new QueryBus() {
            @Override
            @SuppressWarnings("unchecked")
            public <R> R ask(Query<R> query) {
                assertThat(query).isInstanceOf(SearchChunks.class);
                assertThat(((SearchChunks) query).ownerId()).isEqualTo(ALICE);
                return (R) searchResults;
            }
        };
        return new ConversationAgent(TestAgents.anAgent(), llmPort, new DocumentSearchTool(queryBus), clock);
    }

    private static ChunkMatchView aChunk(int position) {
        return new ChunkMatchView(REPORT, "rapport.pdf", position, "Rétractation", "Quatorze jours.", 0.9);
    }

    private static Question theQuestion() {
        return new Question("Quel est le délai de rétractation ?");
    }

    @Test
    void answers_a_greeting_without_searching() {
        llmPort.text("Bonjour", ", je vous écoute.");

        ConversationOutcome outcome = agentWith(List.of()).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.CONVERSATIONAL);
        assertThat(emitted).containsExactly("Bonjour, je vous écoute.");
        assertThat(outcome.searches()).isEmpty();
        assertThat(outcome.turns()).isEqualTo(1);
    }

    @Test
    void searches_then_answers_citing_its_sources() {
        llmPort.callsTool(DocumentAgent.SEARCH_TOOL, "délai de rétractation").text("Quatorze jours ", "[1].");

        ConversationOutcome outcome = agentWith(List.of(aChunk(0))).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.GROUNDED);
        assertThat(outcome.answer().sources()).extracting(Source::number).containsExactly(1);
        assertThat(String.join("", emitted)).isEqualTo("Quatorze jours [1].");
        assertThat(outcome.searches()).containsExactly("délai de rétractation");
        assertThat(outcome.turns()).isEqualTo(2);
    }

    @Test
    void never_displays_an_answer_that_searched_without_citing_anything() {
        llmPort.callsTool(DocumentAgent.SEARCH_TOOL, "capitale").text("Canberra est ", "la capitale de l'Australie.");

        ConversationOutcome outcome = agentWith(List.of(aChunk(0))).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.UNGROUNDED);
        assertThat(emitted).containsExactly(TestAgents.NOT_FOUND);
        assertThat(String.join("", emitted)).doesNotContain("Canberra");
    }

    @Test
    void returns_an_error_to_the_model_when_it_invents_a_tool_name() {
        llmPort.callsTool("chercher_sur_internet", "capitale").text("Pardon.");

        agentWith(List.of()).answer(theQuestion(), ALICE, emitted::add);

        assertThat(llmPort.lastToolResult())
                .contains("chercher_sur_internet")
                .contains("n'existe pas")
                .contains(DocumentAgent.SEARCH_TOOL);
    }

    @Test
    void returns_an_error_to_the_model_when_the_required_argument_is_missing() {
        llmPort.callsToolWithoutArgument(DocumentAgent.SEARCH_TOOL).text("Pardon.");

        ConversationOutcome outcome = agentWith(List.of()).answer(theQuestion(), ALICE, emitted::add);

        assertThat(llmPort.lastToolResult()).contains(DocumentAgent.QUESTION_PARAMETER);
        assertThat(outcome.searches()).isEmpty();
        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.CONVERSATIONAL);
    }

    @Test
    void does_not_count_as_a_search_a_search_rejected_by_the_question() {
        llmPort.callsTool(DocumentAgent.SEARCH_TOOL, "capitale").text("Pardon.");
        QueryBus queryBus = new QueryBus() {
            @Override
            public <R> R ask(Query<R> query) {
                throw new InvalidQuestionException("La question ne peut pas dépasser 2 000 caractères.");
            }
        };
        ConversationAgent agent =
                new ConversationAgent(TestAgents.anAgent(), llmPort, new DocumentSearchTool(queryBus), clock);

        ConversationOutcome outcome = agent.answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.searches()).isEmpty();
        assertThat(llmPort.lastToolResult()).contains("refusée");
    }

    @Test
    void does_not_count_as_a_search_a_rejected_tool_call() {
        llmPort.callsTool("chercher_sur_internet", "capitale").text("Canberra.");

        ConversationOutcome outcome = agentWith(List.of()).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.searches()).isEmpty();
        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.CONVERSATIONAL);
    }

    @Test
    void learns_nothing_from_a_repeated_search_and_ends_up_exhausting_its_turns() {
        llmPort.callsTool(DocumentAgent.SEARCH_TOOL, "délai")
                .callsTool(DocumentAgent.SEARCH_TOOL, "délai")
                .callsTool(DocumentAgent.SEARCH_TOOL, "délai")
                .callsTool(DocumentAgent.SEARCH_TOOL, "délai");

        ConversationOutcome outcome = agentWith(List.of(aChunk(0))).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.BUDGET_EXCEEDED);
        assertThat(outcome.turns()).isEqualTo(4);
        assertThat(emitted).containsExactly(TestAgents.NOT_FOUND);
    }

    @Test
    void gives_up_when_the_time_budget_has_run_out() {
        llmPort.onEachTurn(() -> clock.advance(Duration.ofSeconds(130)))
                .callsTool(DocumentAgent.SEARCH_TOOL, "délai")
                .text("Quatorze jours [1].");

        ConversationOutcome outcome = agentWith(List.of(aChunk(0))).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.BUDGET_EXCEEDED);
        assertThat(outcome.turns()).isEqualTo(1);
        assertThat(outcome.searches()).containsExactly("délai");
    }

    @Test
    void lets_the_client_disconnection_propagate() {
        llmPort.text("Bonjour");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> agentWith(List.of()).answer(theQuestion(), ALICE, fragment -> {
                    throw new IllegalStateException("le client a fermé");
                }))
                .withMessageContaining("le client a fermé");
    }

    @Test
    void rejects_an_empty_question_before_calling_the_model() {
        assertThatExceptionOfType(InvalidQuestionException.class)
                .isThrownBy(() -> agentWith(List.of()).validate("   "));
    }

    @Test
    void consumes_a_turn_that_returns_neither_text_nor_tool_call() {
        llmPort.emptyTurn().text("Bonjour");

        ConversationOutcome outcome = agentWith(List.of()).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.turns()).isEqualTo(2);
        assertThat(emitted).containsExactly("Bonjour");
        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.CONVERSATIONAL);
        assertThat(outcome.searches()).isEmpty();
    }

    @Test
    void exhausts_its_turns_when_the_model_never_returns_anything() {
        llmPort.emptyTurn().emptyTurn().emptyTurn().emptyTurn();

        ConversationOutcome outcome = agentWith(List.of()).answer(theQuestion(), ALICE, emitted::add);

        assertThat(outcome.answer().verdict()).isEqualTo(AnswerVerdict.BUDGET_EXCEEDED);
        assertThat(outcome.turns()).isEqualTo(4);
        assertThat(emitted).containsExactly(TestAgents.NOT_FOUND);
    }

    private static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-09-04T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static final class ScriptedLlmPort implements LlmPort {

        private record ScriptedTurn(List<String> fragments, List<ToolCall> toolCalls) {}

        private final Deque<ScriptedTurn> script = new ArrayDeque<>();
        private final List<LlmRequest> received = new ArrayList<>();

        private Runnable onEachTurn = () -> {};

        ScriptedLlmPort onEachTurn(Runnable effect) {
            this.onEachTurn = effect;
            return this;
        }

        ScriptedLlmPort text(String... fragments) {
            script.add(new ScriptedTurn(List.of(fragments), List.of()));
            return this;
        }

        ScriptedLlmPort emptyTurn() {
            script.add(new ScriptedTurn(List.of(), List.of()));
            return this;
        }

        ScriptedLlmPort callsTool(String name, String question) {
            return addToolCall(name, Map.of(DocumentAgent.QUESTION_PARAMETER, question));
        }

        ScriptedLlmPort callsToolWithoutArgument(String name) {
            return addToolCall(name, Map.of());
        }

        private ScriptedLlmPort addToolCall(String name, Map<String, String> arguments) {
            script.add(new ScriptedTurn(List.of(), List.of(new ToolCall("appel-" + script.size(), name, arguments))));
            return this;
        }

        /** The content of the last tool message the loop returned to the model. */
        String lastToolResult() {
            return received.getLast().messages().reversed().stream()
                    .filter(message -> message.role() == LlmMessage.Role.TOOL_RESULT)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Aucun résultat d'outil n'a été rendu au modèle"))
                    .content();
        }

        @Override
        public LlmTurn stream(LlmRequest request, Consumer<String> onToken) {
            received.add(request);
            onEachTurn.run();
            ScriptedTurn turn =
                    script.isEmpty() ? new ScriptedTurn(List.of("Rien à ajouter."), List.of()) : script.poll();
            StringBuilder text = new StringBuilder();
            for (String fragment : turn.fragments()) {
                text.append(fragment);
                onToken.accept(fragment);
            }
            return new LlmTurn(text.toString(), turn.toolCalls());
        }
    }
}
