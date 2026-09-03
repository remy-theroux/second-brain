package xyz.sterenn.secondbrain.knowledge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Remplace le service de vectorisation par un vecteur constant. <strong>Aucun test de la suite
 * n'appelle Ollama</strong> : il faudrait un modèle de 2,2 Go et une machine capable de le
 * servir, pour vérifier une propriété qui n'appartient pas à ce projet.
 *
 * <p>Même dispositif que {@code RecordingNotificationSenderConfiguration} du contexte
 * {@code users} : un bean {@code @Primary} devant l'adapter réel, donc les tests vérifient le
 * <em>port</em>. Elle enregistre au passage les textes reçus, ce qui est la seule façon de
 * constater que ce qui part au modèle est bien le texte <strong>préfixé</strong>.
 *
 * <p>Le bean est partagé par tout le contexte Spring : appeler {@link ConstantEmbeddingPort#clear()}
 * en {@code @BeforeEach}, le rollback de la transaction de test ne le vide pas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConstantEmbeddingPortConfiguration {

    @Bean
    @Primary
    public ConstantEmbeddingPort constantEmbeddingPort() {
        return new ConstantEmbeddingPort();
    }

    public static class ConstantEmbeddingPort implements EmbeddingPort {

        private final List<String> recus = new CopyOnWriteArrayList<>();
        private final AtomicBoolean enPanne = new AtomicBoolean(false);

        @Override
        public List<Embedding> embed(List<String> texts) {
            if (enPanne.get()) {
                // Le message nomme la vectorisation, comme celui de l'adapter réel : c'est
                // lui que le test de bout en bout retrouve sur le document en échec.
                throw new EmbeddingUnavailableException(
                        "Le service de vectorisation n'a pas répondu : ce document n'a pas pu être indexé.");
            }
            recus.addAll(texts);
            return texts.stream().map(texte -> KnowledgeFixture.unVecteur(0.5f)).toList();
        }

        /** Les textes tels qu'ils sont partis au modèle — préfixe compris. */
        public List<String> textesRecus() {
            return List.copyOf(recus);
        }

        public void tombeEnPanne() {
            enPanne.set(true);
        }

        public void clear() {
            recus.clear();
            enPanne.set(false);
        }
    }
}
