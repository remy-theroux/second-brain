package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UploadDocumentTest {

    @Test
    void never_returns_the_document_content() {
        byte[] content = "un secret bien gardé".getBytes(StandardCharsets.UTF_8);

        String rendered = new UploadDocument(UUID.randomUUID(), "rapport.pdf", content).toString();

        assertThat(rendered).doesNotContain("secret").contains("rapport.pdf").contains(content.length + " octets");
    }
}
