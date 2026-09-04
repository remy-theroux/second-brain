package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.function.Consumer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;

/**
 * Port sortant vers le service de génération : un tour, pas une conversation. L'appel bloque
 * jusqu'à la fin du tour ; les fragments de texte sont remis à {@code onToken} au fil de leur
 * production. Une exception levée par {@code onToken} interrompt le tour — c'est ainsi que la
 * déconnexion d'un client arrête la génération.
 */
public interface LlmPort {

    LlmTurn stream(LlmRequest request, Consumer<String> onToken);
}
