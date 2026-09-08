package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * A refusal is always the <em>last</em> HTTP call of its test: the exception marks the enclosing
 * transaction rollback-only, and a second call behind it would fail on an
 * {@code UnexpectedRollbackException}. Whatever is left to check after a refusal is read through
 * the port.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReplaceDocumentContentControllerTest {

    private static final String PASSWORD = "chevalpile42";
    private static final byte[] FIRST = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND = "le contenu revu du rapport".getBytes(StandardCharsets.UTF_8);
    private static final String PREVIOUS_TEXT =
            "Le texte qu'avait rendu la version precedente de ce document, assez long pour etre exploitable.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStorage documentStorage;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID alice;
    private String aliceToken;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    @AfterEach
    void erase_the_originals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    private static MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }

    private UUID upload(String token, UUID ownerId, String filename, byte[] content) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(file(filename, content))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(ownerId).getFirst().getId();
    }

    private Document reload(UUID documentId, UUID ownerId) {
        return documentRepository.findByIdAndOwnerId(documentId, ownerId).orElseThrow();
    }

    private void indexWithoutTheWorker(UUID document) {
        Instant now = Instant.now();
        textExtractionRepository.save(TextExtraction.of(
                document, new ExtractedText(List.of(TextBlock.of("Introduction", 1, PREVIOUS_TEXT))), now));
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document, 0, new Chunk("Introduction", PREVIOUS_TEXT), KnowledgeFixture.aVector(0.1f), now)));
    }

    @Test
    void replaces_the_content_of_a_document() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("rapport-v2.pdf", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk());

        assertThat(reload(document, alice)).satisfies(replaced -> {
            assertThat(replaced.getFilename()).isEqualTo("rapport-v2.pdf");
            assertThat(replaced.getChecksum()).isEqualTo(Checksum.of(SECOND));
            assertThat(replaced.getSizeBytes()).isEqualTo(SECOND.length);
            assertThat(replaced.getStatus()).isEqualTo(DocumentStatus.PENDING);
        });
        assertThat(documentStorage.read(document))
                .hasValueSatisfying(stored -> assertThat(stored).isEqualTo(SECOND));
    }

    @Test
    void keeps_the_document_in_place_rather_than_creating_a_second_one() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("rapport.pdf", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk());

        assertThat(documentRepository.findAllByOwnerId(alice))
                .singleElement()
                .extracting(Document::getId)
                .isEqualTo(document);
    }

    @Test
    void does_nothing_when_the_content_is_strictly_identical() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);
        Document indexed = reload(document, alice);
        indexed.markTextExtracted();
        indexed.markIndexed();
        documentRepository.save(indexed);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("rapport.pdf", FIRST))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk());

        assertThat(reload(document, alice).getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void drops_the_text_and_the_chunks_of_the_content_it_replaces() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);
        indexWithoutTheWorker(document);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("rapport.pdf", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk());

        assertThat(textExtractionRepository.findByDocumentId(document)).isEmpty();
        assertThat(textChunkRepository.findByDocumentId(document)).isEmpty();
    }

    @Test
    void rejects_a_content_already_held_by_another_document() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);
        mockMvc.perform(multipart("/api/documents")
                        .file(file("notes.md", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isCreated());
        UUID other = documentRepository.findAllByOwnerId(alice).stream()
                .filter(candidate -> !candidate.getId().equals(document))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("rapport.pdf", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingDocumentId").value(other.toString()))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejects_an_unsupported_format() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("programme.exe", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejects_an_empty_file() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf", FIRST);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", document)
                        .file(file("rapport.pdf", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.file").isNotEmpty());
    }

    @Test
    void refuses_to_replace_the_document_of_another_account() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        UUID bobsDocument = upload(KnowledgeFixture.token(accessTokenIssuer, bob), bob, "chez-bob.pdf", FIRST);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", bobsDocument)
                        .file(file("chez-alice.pdf", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());

        assertThat(reload(bobsDocument, bob).getChecksum()).isEqualTo(Checksum.of(FIRST));
    }

    @Test
    void refuses_an_unknown_document() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", UUID.randomUUID())
                        .file(file("rapport.pdf", SECOND))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuses_an_anonymous_request() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", UUID.randomUUID())
                        .file(file("rapport.pdf", SECOND)))
                .andExpect(status().isUnauthorized());
    }
}
