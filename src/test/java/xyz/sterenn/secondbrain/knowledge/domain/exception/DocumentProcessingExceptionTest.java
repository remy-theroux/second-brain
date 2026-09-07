package xyz.sterenn.secondbrain.knowledge.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentProcessingExceptionTest {

    @Test
    void an_extraction_refusal_is_a_processing_refusal() {
        assertThat(new UnreadableDocumentException()).isInstanceOf(DocumentProcessingException.class);
        assertThat(new UnextractableDocumentException()).isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void an_embedding_refusal_is_a_processing_refusal() {
        assertThat(new EmbeddingUnavailableException("Le service de vectorisation est injoignable."))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void every_processing_refusal_carries_a_displayable_message() {
        assertThat(new UnextractableDocumentException().getMessage()).isNotBlank();
        assertThat(new UnreadableDocumentException().getMessage()).isNotBlank();
    }
}
