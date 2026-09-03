package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

/**
 * Adapter jtokkit du port {@link TokenCounter}, en {@code cl100k_base}.
 *
 * <p>Aucun réseau, aucun modèle à télécharger : les tables BPE voyagent dans le jar. Le
 * registre est <em>paresseux</em> — il ne charge que l'encodage demandé, là où le registre
 * par défaut les charge tous, dont ceux dont ce projet n'a que faire.
 *
 * <p>L'{@link Encoding} est construit une fois : il est immuable et sûr en accès concurrent,
 * et le construire à chaque appel relirait les tables BPE pour chaque paragraphe d'un
 * document.
 *
 * <p>Package-private : rien au-dehors ne doit dépendre d'autre chose que du port. Voisin de
 * {@code OllamaEmbeddingAdapter} parce que les deux servent le même modèle — l'un le mesure,
 * l'autre l'interroge.
 */
@Component
class JtokkitTokenCounter implements TokenCounter {

    private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.countTokens(text);
    }
}
