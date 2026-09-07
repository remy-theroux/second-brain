package xyz.sterenn.secondbrain.knowledge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.exception.LlmUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;

@TestConfiguration(proxyBeanMethods = false)
public class ScriptedLlmPortConfiguration {

    @Bean
    @Primary
    public ScriptedLlmPort scriptedLlmPort() {
        return new ScriptedLlmPort();
    }

    public static class ScriptedLlmPort implements LlmPort {

        private record ScriptedTurn(List<String> fragments, List<ToolCall> toolCalls) {}

        private final Deque<ScriptedTurn> script = new ArrayDeque<>();
        private volatile boolean failing;

        public ScriptedLlmPort text(String... fragments) {
            script.add(new ScriptedTurn(List.of(fragments), List.of()));
            return this;
        }

        public ScriptedLlmPort callsTool(String name, String parameter, String value) {
            script.add(new ScriptedTurn(
                    List.of(), List.of(new ToolCall("appel-" + script.size(), name, Map.of(parameter, value)))));
            return this;
        }

        public ScriptedLlmPort willFail() {
            failing = true;
            return this;
        }

        public void clear() {
            script.clear();
            failing = false;
        }

        @Override
        public LlmTurn stream(LlmRequest request, Consumer<String> onToken) {
            if (failing) {
                throw new LlmUnavailableException("Le service de génération n'a pas répondu.");
            }
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
