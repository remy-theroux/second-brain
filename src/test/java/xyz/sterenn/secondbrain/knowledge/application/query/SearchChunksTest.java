package xyz.sterenn.secondbrain.knowledge.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest
@Transactional
class SearchChunksTest {

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private RecordingEmbeddingPort recordingEmbeddingPort;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void the_question_always_embeds_the_same_way() {
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.willAnswer(KnowledgeFixture.aQuestion());
    }

    @AfterEach
    void leaves_the_embedding_port_as_it_found_it() {
        recordingEmbeddingPort.clear();
    }

    @Test
    void returns_first_the_chunk_that_carries_the_answer() {
        UUID alice = anAccount("alice@exemple.fr");
        Document document = anUploadedDocument(alice, "rapport.md");
        textChunkRepository.saveAll(List.of(
                aChunk(document, 0, "Une digression sans rapport.", 0.1f),
                aChunk(document, 1, "La réponse est quarante-deux.", 1f),
                aChunk(document, 2, "Un passage à peu près sur le sujet.", 0.6f)));

        List<ChunkMatchView> results = queryBus.ask(new SearchChunks("Quelle est la réponse ?", alice));

        assertThat(results).extracting(ChunkMatchView::text).startsWith("La réponse est quarante-deux.");
    }

    @Test
    void returns_for_each_chunk_its_content_its_document_its_position_and_its_score() {
        UUID alice = anAccount("bruno@exemple.fr");
        Document document = anUploadedDocument(alice, "rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                2,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.aNearbyVector(1f),
                Instant.now())));

        List<ChunkMatchView> results = queryBus.ask(new SearchChunks("Une question", alice));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.documentId()).isEqualTo(document.getId());
            assertThat(result.filename()).isEqualTo("rapport-annuel.md");
            assertThat(result.position()).isEqualTo(2);
            assertThat(result.heading()).isEqualTo("Introduction");
            assertThat(result.text()).isEqualTo("Le corps de l'extrait.");
            assertThat(result.similarity()).isGreaterThan(0.99d);
        });
    }

    @Test
    void returns_an_empty_list_for_an_empty_knowledge_base() {
        UUID alice = anAccount("clara@exemple.fr");

        assertThat(queryBus.ask(new SearchChunks("Une question", alice))).isEmpty();
    }

    @Test
    void does_not_return_the_chunks_of_another_account() {
        UUID alice = anAccount("diane@exemple.fr");
        UUID bob = anAccount("edgar@exemple.fr");
        textChunkRepository.saveAll(List.of(
                aChunk(anUploadedDocument(alice, "a-elle.md"), 0, "Le sien.", 0.5f),
                aChunk(anUploadedDocument(bob, "a-lui.md"), 0, "Celui de Bob.", 1f)));

        assertThat(queryBus.ask(new SearchChunks("Une question", alice)))
                .extracting(ChunkMatchView::text)
                .containsExactly("Le sien.");
    }

    @Test
    void never_returns_more_than_eight_chunks() {
        UUID alice = anAccount("fatou@exemple.fr");
        Document document = anUploadedDocument(alice, "long.md");
        textChunkRepository.saveAll(IntStream.range(0, 12)
                .mapToObj(position -> aChunk(document, position, "Extrait " + position, 0.5f))
                .toList());

        assertThat(queryBus.ask(new SearchChunks("Une question", alice))).hasSize(8);
    }

    @Test
    void rejects_an_empty_question() {
        UUID alice = anAccount("gaspard@exemple.fr");

        assertThatThrownBy(() -> queryBus.ask(new SearchChunks("   ", alice)))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("La question ne peut pas être vide.");
    }

    @Test
    void embeds_the_question_exactly_as_it_was_asked() {
        UUID alice = anAccount("helena@exemple.fr");

        queryBus.ask(new SearchChunks("  Qui a signé le rapport ?  ", alice));

        assertThat(recordingEmbeddingPort.receivedTexts()).containsExactly("Qui a signé le rapport ?");
    }

    private static TextChunk aChunk(Document document, int position, String body, float closeness) {
        return TextChunk.of(
                document.getId(),
                position,
                new Chunk("Titre", body),
                KnowledgeFixture.aNearbyVector(closeness),
                Instant.now());
    }

    private UUID anAccount(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private Document anUploadedDocument(UUID owner, String name) {
        byte[] bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(owner, name, DocumentFormat.MARKDOWN, Checksum.of(bytes), bytes.length));
    }
}
