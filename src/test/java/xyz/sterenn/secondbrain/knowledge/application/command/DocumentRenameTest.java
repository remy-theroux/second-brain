package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DocumentRenameTest {

    private static final byte[] CONTENT = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

    private static final Checksum CHECKSUM = Checksum.of(CONTENT);

    private static final String FAILURE = "Ce document ne contient pas de texte exploitable.";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void changes_the_name_and_nothing_else() {
        Document document = aFailedDocument();

        commandBus.dispatch(new RenameDocument(document.getOwnerId(), document.getId(), "rapport-2026.pdf"));

        assertThat(reload(document)).satisfies(renamed -> {
            assertThat(renamed.getFilename()).isEqualTo("rapport-2026.pdf");
            assertThat(renamed.getChecksum()).isEqualTo(CHECKSUM);
            assertThat(renamed.getFormat()).isEqualTo(DocumentFormat.PDF);
            assertThat(renamed.getSizeBytes()).isEqualTo(CONTENT.length);
            assertThat(renamed.getStatus()).isEqualTo(DocumentStatus.FAILED);
            assertThat(renamed.getErrorMessage()).isEqualTo(FAILURE);
        });
    }

    @Test
    void keeps_the_chunks_of_the_document() {
        Document document = aReadyDocument();
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(), 0, new Chunk("Titre", "Un corps."), KnowledgeFixture.aVector(0.5f), Instant.now())));

        commandBus.dispatch(new RenameDocument(document.getOwnerId(), document.getId(), "rapport-2026.pdf"));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(1);
    }

    @Test
    void bounds_a_name_longer_than_the_column() {
        Document document = aReadyDocument();
        String tooLong = "n".repeat(Document.MAX_FILENAME_LENGTH + 10) + ".pdf";

        commandBus.dispatch(new RenameDocument(document.getOwnerId(), document.getId(), tooLong));

        assertThat(reload(document).getFilename()).hasSize(Document.MAX_FILENAME_LENGTH);
    }

    @Test
    void refuses_a_blank_name() {
        Document document = aReadyDocument();

        // Last call of the test: the refusal marks the enclosing transaction rollback-only.
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(
                        () -> commandBus.dispatch(new RenameDocument(document.getOwnerId(), document.getId(), "   ")));
    }

    @Test
    void refuses_to_rename_the_document_of_another_account() {
        Document document = aReadyDocument();

        // Last call of the test: the refusal marks the enclosing transaction rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() ->
                        commandBus.dispatch(new RenameDocument(anAccount(), document.getId(), "rapport-vole.pdf")));
    }

    private Document aReadyDocument() {
        Document document = Document.upload(anAccount(), "rapport.pdf", DocumentFormat.PDF, CHECKSUM, CONTENT.length);
        document.markTextExtracted();
        document.markIndexed();
        return documentRepository.save(document);
    }

    private Document aFailedDocument() {
        Document document = Document.upload(anAccount(), "rapport.pdf", DocumentFormat.PDF, CHECKSUM, CONTENT.length);
        document.markProcessingFailed(FAILURE);
        return documentRepository.save(document);
    }

    private UUID anAccount() {
        return userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
    }

    private Document reload(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }
}
