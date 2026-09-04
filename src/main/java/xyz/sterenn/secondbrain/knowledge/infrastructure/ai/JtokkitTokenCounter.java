package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

/**
 * Le registre paresseux ne charge que l'encodage demandé, là où le registre par défaut les
 * charge tous. L'{@link Encoding}, immuable et sûr en accès concurrent, est construit une
 * fois : le reconstruire relirait les tables BPE à chaque appel.
 */
@Component
class JtokkitTokenCounter implements TokenCounter {

    private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.countTokens(text);
    }
}
