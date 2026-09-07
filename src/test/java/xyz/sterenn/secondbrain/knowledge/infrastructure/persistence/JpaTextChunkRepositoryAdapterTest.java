package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaTextChunkRepositoryAdapterTest {

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void keeps_the_chunks_in_order_with_their_heading_and_their_body() {
        Document document = anUploadedDocument("mona@exemple.fr");

        textChunkRepository.saveAll(List.of(
                aChunk(document, 0, "Introduction", "Le premier extrait."),
                aChunk(document, 1, "Introduction", "Le deuxième extrait."),
                aChunk(document, 2, "", "Le troisième, sans titre.")));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getPosition)
                .containsExactly(0, 1, 2);
        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getHeading)
                .containsExactly("Introduction", "Introduction", "");
    }

    @Test
    void returns_the_vector_as_it_was_stored() {
        Document document = anUploadedDocument("nadir@exemple.fr");
        Embedding vector = KnowledgeFixture.aVector(0.25f);

        textChunkRepository.saveAll(
                List.of(TextChunk.of(document.getId(), 0, new Chunk("Titre", "Un corps."), vector, Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(reloaded -> assertThat(reloaded.getEmbedding()).isEqualTo(vector));
    }

    @Test
    void returns_the_domain_chunk_as_it_was_stored() {
        Document document = anUploadedDocument("olga@exemple.fr");
        Chunk chunk = new Chunk("Introduction", "Un corps bien à lui.");

        textChunkRepository.saveAll(
                List.of(TextChunk.of(document.getId(), 0, chunk, KnowledgeFixture.aVector(0.5f), Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(reloaded -> assertThat(reloaded.chunk()).isEqualTo(chunk));
    }

    @Test
    void keeps_a_chunk_far_longer_than_255_characters() {
        Document document = anUploadedDocument("pierre@exemple.fr");
        String veryLong = "Un corps qui déborde largement d'une colonne de 255 caractères. ".repeat(50);

        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(), 0, new Chunk("", veryLong), KnowledgeFixture.aVector(0.1f), Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(reloaded -> assertThat(reloaded.getText()).isEqualTo(veryLong.strip()));
    }

    @Test
    void deletes_the_chunks_of_a_document() {
        Document document = anUploadedDocument("quentin@exemple.fr");
        textChunkRepository.saveAll(List.of(aChunk(document, 0, "Titre", "Un corps.")));

        textChunkRepository.deleteByDocumentId(document.getId());

        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void a_second_set_of_chunks_replaces_the_first_after_deletion() {
        // AMQP delivers at least once, and (document_id, chunk_position) is UNIQUE: without the
        // prior deletion, the second write would hit the constraint.
        Document document = anUploadedDocument("rosa@exemple.fr");
        textChunkRepository.saveAll(List.of(aChunk(document, 0, "Titre", "Première version.")));

        textChunkRepository.deleteByDocumentId(document.getId());
        textChunkRepository.saveAll(List.of(aChunk(document, 0, "Titre", "Deuxième version.")));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(reloaded -> assertThat(reloaded.getText()).isEqualTo("Deuxième version."));
    }

    @Test
    void stays_silent_when_no_chunk_has_been_stored() {
        assertThat(textChunkRepository.findByDocumentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void returns_the_chunks_from_the_nearest_to_the_farthest() {
        UUID alice = anAccount("sylvie@exemple.fr");
        Document document = anUploadedDocument(alice, "rapport.md");
        textChunkRepository.saveAll(List.of(
                anOrientedChunk(document, 0, "Le plus lointain.", 0.1f),
                anOrientedChunk(document, 1, "Le plus proche.", 1f),
                anOrientedChunk(document, 2, "L'intermédiaire.", 0.6f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.aQuestion(), 8))
                .extracting(match -> match.chunk().text())
                .containsExactly("Le plus proche.", "L'intermédiaire.", "Le plus lointain.");
    }

    @Test
    void returns_the_document_name_the_position_and_the_heading_of_each_chunk() {
        UUID alice = anAccount("thomas@exemple.fr");
        Document document = anUploadedDocument(alice, "rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                3,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.aNearbyVector(1f),
                Instant.now())));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.aQuestion(), 8))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.documentId()).isEqualTo(document.getId());
                    assertThat(match.filename()).isEqualTo("rapport-annuel.md");
                    assertThat(match.position()).isEqualTo(3);
                    assertThat(match.chunk()).isEqualTo(new Chunk("Introduction", "Le corps de l'extrait."));
                });
    }

    @Test
    void returns_a_similarity_of_one_for_a_chunk_whose_vector_is_the_question_one() {
        UUID alice = anAccount("ursula@exemple.fr");
        Document document = anUploadedDocument(alice, "notes.md");
        textChunkRepository.saveAll(List.of(anOrientedChunk(document, 0, "Identique.", 1f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.aQuestion(), 8))
                .singleElement()
                .satisfies(match -> assertThat(match.similarity()).isCloseTo(1d, within(0.0001d)));
    }

    @Test
    void does_not_return_the_chunks_of_another_account() {
        UUID alice = anAccount("valentine@exemple.fr");
        UUID bob = anAccount("walid@exemple.fr");
        Document hers = anUploadedDocument(alice, "a-elle.md");
        Document bobs = anUploadedDocument(bob, "a-lui.md");
        textChunkRepository.saveAll(
                List.of(anOrientedChunk(hers, 0, "Le sien.", 0.5f), anOrientedChunk(bobs, 0, "Celui de Bob.", 1f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.aQuestion(), 8))
                .extracting(match -> match.chunk().text())
                .containsExactly("Le sien.");
    }

    @Test
    void caps_the_number_of_returned_chunks() {
        UUID alice = anAccount("xavier@exemple.fr");
        Document document = anUploadedDocument(alice, "long.md");
        textChunkRepository.saveAll(IntStream.range(0, 12)
                .mapToObj(position -> anOrientedChunk(document, position, "Extrait " + position, 0.5f))
                .toList());

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.aQuestion(), 8))
                .hasSize(8);
    }

    @Test
    void stays_silent_when_the_account_carries_no_chunk() {
        UUID alice = anAccount("yasmine@exemple.fr");

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.aQuestion(), 8))
                .isEmpty();
    }

    private static TextChunk anOrientedChunk(Document document, int position, String body, float proximity) {
        return TextChunk.of(
                document.getId(),
                position,
                new Chunk("Titre", body),
                KnowledgeFixture.aNearbyVector(proximity),
                Instant.now());
    }

    private static TextChunk aChunk(Document document, int position, String heading, String body) {
        return TextChunk.of(
                document.getId(), position, new Chunk(heading, body), KnowledgeFixture.aVector(0.5f), Instant.now());
    }

    private UUID anAccount(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private Document anUploadedDocument(UUID ownerId, String filename) {
        byte[] bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(ownerId, filename, DocumentFormat.MARKDOWN, Checksum.of(bytes), bytes.length));
    }

    private Document anUploadedDocument(String email) {
        return anUploadedDocument(anAccount(email), "notes.md");
    }
}
