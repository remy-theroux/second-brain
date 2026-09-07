package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest
@Transactional
class IndexDocumentTextTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingEmbeddingPort embeddingPort;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    @BeforeEach
    void clearsTheStub() {
        embeddingPort.clear();
    }

    @AfterEach
    void clearsTheStubAndTheOriginals() {
        // The stub is a singleton of the Spring context, and the object storage takes part in no
        // transaction: @Transactional rolls back neither of them.
        embeddingPort.clear();
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void stores_the_embedded_chunks_and_marks_the_document_ready() {
        Document document = anExtractedDocument("structure.md", Fixtures.STRUCTURED_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> chunks = textChunkRepository.findByDocumentId(document.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.getEmbedding().values()).hasSize(EmbeddingPolicy.DIMENSIONS));
        assertThat(reload(document).getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void numbers_the_chunks_in_document_order() {
        Document document = anExtractedDocument("structure.md", Fixtures.STRUCTURED_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> chunks = textChunkRepository.findByDocumentId(document.getId());
        assertThat(chunks)
                .extracting(TextChunk::getPosition)
                .containsExactlyElementsOf(
                        IntStream.range(0, chunks.size()).boxed().toList());
        List<TextBlock> blocks = textExtractionRepository
                .findByDocumentId(document.getId())
                .orElseThrow()
                .text()
                .blocks();
        assertThat(chunks)
                .extracting(TextChunk::getHeading)
                .containsExactlyElementsOf(
                        blocks.stream().map(TextBlock::getHeading).toList());
        assertThat(chunks.getFirst().getText()).isEqualTo(blocks.getFirst().getText());
    }

    @Test
    void stores_under_each_chunk_the_vector_of_its_own_text() {
        Document document = anExtractedDocument("structure.md", Fixtures.STRUCTURED_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        List<TextChunk> chunks = textChunkRepository.findByDocumentId(document.getId());
        assertThat(chunks).isNotEmpty();
        for (int position = 0; position < chunks.size(); position++) {
            assertThat(chunks.get(position).getEmbedding()).isEqualTo(RecordingEmbeddingPort.vectorAtRank(position));
        }
    }

    @Test
    void embeds_a_text_prefixed_with_the_document_name_and_its_section() {
        Document document = anExtractedDocument("structure.md", Fixtures.STRUCTURED_MD);

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(embeddingPort.receivedTexts()).isNotEmpty().allSatisfy(text -> assertThat(text)
                .startsWith("Document: structure.md"));
        assertThat(embeddingPort.receivedTexts())
                .anySatisfy(text -> assertThat(text).contains("— Section: "));
        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .allSatisfy(chunk -> assertThat(chunk.getText()).doesNotContain("Document: structure.md"));
    }

    @Test
    void a_second_indexing_replaces_the_chunks_without_duplicating_them() {
        Document document = anExtractedDocument("structure.md", Fixtures.STRUCTURED_MD);
        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));
        int firstCount = textChunkRepository.findByDocumentId(document.getId()).size();

        commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(firstCount);
    }

    @Test
    void lets_the_embedding_service_refusal_propagate() {
        Document document = anExtractedDocument("notes.txt", Fixtures.RAW_TXT);
        embeddingPort.willFail();

        // Last call of the test: the refusal marks the enclosing transaction rollback-only.
        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> commandBus.dispatch(new IndexDocumentText(document.getId(), document.getOwnerId())));
    }

    @Test
    void rejects_a_document_that_does_not_belong_to_the_requester() {
        Document document = anExtractedDocument("notes.txt", Fixtures.RAW_TXT);

        // Last call of the test, same reason.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(new IndexDocumentText(document.getId(), UUID.randomUUID())));
    }

    private Document anExtractedDocument(String filename, String fixture) {
        UUID owner = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] content = Fixtures.read(fixture);
        commandBus.dispatch(new UploadDocument(owner, filename, content));
        Document document = documentRepository
                .findByOwnerIdAndChecksum(owner, Checksum.of(content))
                .orElseThrow();
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        return document;
    }

    private Document reload(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }
}
