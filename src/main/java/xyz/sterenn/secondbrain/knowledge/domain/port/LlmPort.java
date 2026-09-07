package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.function.Consumer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;

/**
 * Outbound port to the generation service: one turn, not a conversation. The call blocks until
 * the end of the turn; text fragments are handed to {@code onToken} as they are produced. An
 * exception thrown by {@code onToken} interrupts the turn — this is how a client disconnect
 * stops generation.
 *
 * <p>All of the turn's assistant text must go through {@code onToken}; {@link
 * xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn#text()} is only a recap, never
 * the sole carrier.
 */
public interface LlmPort {

    LlmTurn stream(LlmRequest request, Consumer<String> onToken);
}
