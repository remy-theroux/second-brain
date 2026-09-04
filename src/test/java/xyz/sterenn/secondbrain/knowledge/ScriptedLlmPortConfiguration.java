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

        private record TourScripte(List<String> fragments, List<ToolCall> appels) {}

        private final Deque<TourScripte> script = new ArrayDeque<>();
        private volatile boolean enPanne;

        public ScriptedLlmPort texte(String... fragments) {
            script.add(new TourScripte(List.of(fragments), List.of()));
            return this;
        }

        public ScriptedLlmPort appelleOutil(String nom, String parametre, String valeur) {
            script.add(new TourScripte(
                    List.of(), List.of(new ToolCall("appel-" + script.size(), nom, Map.of(parametre, valeur)))));
            return this;
        }

        public ScriptedLlmPort tombeEnPanne() {
            enPanne = true;
            return this;
        }

        public void clear() {
            script.clear();
            enPanne = false;
        }

        @Override
        public LlmTurn stream(LlmRequest request, Consumer<String> onToken) {
            if (enPanne) {
                throw new LlmUnavailableException("Le service de génération n'a pas répondu.");
            }
            TourScripte tour =
                    script.isEmpty() ? new TourScripte(List.of("Rien à ajouter."), List.of()) : script.poll();
            StringBuilder texte = new StringBuilder();
            for (String fragment : tour.fragments()) {
                texte.append(fragment);
                onToken.accept(fragment);
            }
            return new LlmTurn(texte.toString(), tour.appels());
        }
    }
}
