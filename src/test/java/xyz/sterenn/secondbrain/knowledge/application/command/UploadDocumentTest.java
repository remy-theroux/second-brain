package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UploadDocumentTest {

    @Test
    void ne_rend_jamais_le_contenu_du_document() {
        byte[] contenu = "un secret bien gardé".getBytes(StandardCharsets.UTF_8);

        String rendu = new UploadDocument(UUID.randomUUID(), "rapport.pdf", contenu).toString();

        assertThat(rendu).doesNotContain("secret").contains("rapport.pdf").contains(contenu.length + " octets");
    }
}
