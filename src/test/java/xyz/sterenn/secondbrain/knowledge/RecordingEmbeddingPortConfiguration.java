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
 * Remplace le service de vectorisation par un enregistreur en mémoire. <strong>Aucun test de
 * la suite n'appelle Ollama</strong> : il faudrait un modèle de 2,2 Go et une machine capable
 * de le servir, pour vérifier une propriété qui n'appartient pas à ce projet.
 *
 * <p>Même dispositif que {@code RecordingNotificationSenderConfiguration} du contexte
 * {@code users} : un bean {@code @Primary} devant l'adapter réel, donc les tests vérifient le
 * <em>port</em>. Elle enregistre au passage les textes reçus, ce qui est la seule façon de
 * constater que ce qui part au modèle est bien le texte <strong>préfixé</strong>.
 *
 * <p><strong>Le vecteur varie avec le rang du texte</strong>, et non plus constant : un
 * appariement extrait/vecteur qui échangerait deux rangs doit produire une différence
 * observable, ce qu'un même vecteur pour tous les textes ne peut pas révéler. Voir
 * {@link RecordingEmbeddingPort#vecteurDuRang(int)}.
 *
 * <p>Le bean est partagé par tout le contexte Spring : appeler {@link RecordingEmbeddingPort#clear()}
 * en {@code @BeforeEach} <strong>et</strong> en {@code @AfterEach}, le rollback de la
 * transaction de test ne le vide pas — et l'oubli le plus dangereux est celui du drapeau de
 * panne, qu'un test suivant hériterait sans le vouloir.
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
                // Le message nomme la vectorisation, comme celui de l'adapter réel : c'est
                // lui que le test de bout en bout retrouve sur le document en échec.
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

        /**
         * Le vecteur qu'un texte reçoit selon son rang d'arrivée : distinct à chaque rang, donc
         * traçable. Un appariement extrait/vecteur décalé d'un cran se voit dans l'assertion.
         */
        public static Embedding vecteurDuRang(int rang) {
            return KnowledgeFixture.unVecteur(0.01f * (rang + 1));
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
