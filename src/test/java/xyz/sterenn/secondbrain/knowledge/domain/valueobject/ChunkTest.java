package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ChunkTest {

    @Test
    void carries_its_section_heading_and_its_body() {
        Chunk chunk = new Chunk("Introduction", "Le corps de la section.");

        assertThat(chunk.heading()).isEqualTo("Introduction");
        assertThat(chunk.text()).isEqualTo("Le corps de la section.");
    }

    @Test
    void accepts_a_chunk_without_a_heading() {
        assertThat(new Chunk("", "Un document sans titre.").heading()).isEmpty();
    }

    @Test
    void rejects_a_chunk_without_a_body() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Chunk("Introduction", "   "));
    }

    @Test
    void rejects_a_missing_heading() {
        assertThatNullPointerException().isThrownBy(() -> new Chunk(null, "Un corps."));
    }

    @Test
    void two_chunks_with_the_same_content_are_equal() {
        assertThat(new Chunk("Titre", "Un corps.")).isEqualTo(new Chunk("Titre", "Un corps."));
    }

    @Test
    void introduces_itself_with_its_document_and_its_section() {
        Chunk chunk = new Chunk("Introduction", "Le corps de la section.");

        assertThat(chunk.contextualised("rapport.pdf"))
                .isEqualTo("Document: rapport.pdf — Section: Introduction\n\nLe corps de la section.");
    }

    @Test
    void introduces_itself_with_its_document_alone_when_the_section_has_no_heading() {
        Chunk chunk = new Chunk("", "Le corps de la section.");

        assertThat(chunk.contextualised("rapport.pdf")).isEqualTo("Document: rapport.pdf\n\nLe corps de la section.");
    }

    @Test
    void refuses_to_introduce_itself_without_a_document_name() {
        assertThatNullPointerException().isThrownBy(() -> new Chunk("Introduction", "Un corps.").contextualised(null));
    }
}
