package xyz.sterenn.secondbrain.knowledge;

import java.util.ArrayList;
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
 * Le bean est partagé par tout le contexte Spring et le rollback de la transaction de test ne le
 * vide pas : appeler {@link RecordingEmbeddingPort#clear()} en {@code @BeforeEach}
 * <strong>et</strong> en {@code @AfterEach}, faute de quoi le drapeau de panne se transmet.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingEmbeddingPortConfiguration {

    @Bean
    @Primary
    public RecordingEmbeddingPort recordingEmbeddingPort() {
        return new RecordingEmbeddingPort();
    }

    public static class RecordingEmbeddingPort implements EmbeddingPort {

        private final List<String> recus = new CopyOnWriteArrayList<>();
        private final AtomicBoolean enPanne = new AtomicBoolean(false);

        @Override
        public List<Embedding> embed(List<String> texts) {
            if (enPanne.get()) {
                throw new EmbeddingUnavailableException(
                        "Le service de vectorisation n'a pas répondu : ce document n'a pas pu être indexé.");
            }
            List<Embedding> vecteurs = new ArrayList<>();
            for (String texte : texts) {
                vecteurs.add(vecteurDuRang(recus.size()));
                recus.add(texte);
            }
            return vecteurs;
        }

        /** Un vecteur distinct par rang : un appariement décalé d'un cran se voit dans l'assertion. */
        public static Embedding vecteurDuRang(int rang) {
            return KnowledgeFixture.unVecteur(0.01f * (rang + 1));
        }

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
