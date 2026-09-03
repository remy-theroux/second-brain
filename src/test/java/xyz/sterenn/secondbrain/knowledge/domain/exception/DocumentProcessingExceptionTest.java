package xyz.sterenn.secondbrain.knowledge.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Un test de filiation, et il n'est pas décoratif : {@code KnowledgeEventListener.motif()}
 * décide de montrer ou non un message à l'utilisateur sur un seul {@code instanceof}. Le jour
 * où un refus sortirait de cette famille, son message soigné serait silencieusement remplacé
 * par « Le traitement de ce document a échoué de façon inattendue. » — une panne qui ne se
 * verrait qu'à l'écran d'un utilisateur, jamais dans un test de comportement.
 */
class DocumentProcessingExceptionTest {

    @Test
    void un_refus_d_extraction_est_un_refus_de_traitement() {
        assertThat(new UnreadableDocumentException()).isInstanceOf(DocumentProcessingException.class);
        assertThat(new UnextractableDocumentException()).isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void un_refus_de_vectorisation_est_un_refus_de_traitement() {
        assertThat(new EmbeddingUnavailableException("Le service de vectorisation est injoignable."))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void tout_refus_de_traitement_porte_un_message_affichable() {
        assertThat(new UnextractableDocumentException().getMessage()).isNotBlank();
        assertThat(new UnreadableDocumentException().getMessage()).isNotBlank();
    }
}
