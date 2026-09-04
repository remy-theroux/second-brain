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
    void la_question_se_vectorise_toujours_de_la_meme_facon() {
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.repondra(KnowledgeFixture.uneQuestion());
    }

    @AfterEach
    void rend_le_port_de_vectorisation_comme_il_l_a_trouve() {
        recordingEmbeddingPort.clear();
    }

    @Test
    void rend_l_extrait_qui_porte_la_reponse_en_tete() {
        UUID alice = unCompte("alice@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport.md");
        textChunkRepository.saveAll(List.of(
                unExtrait(document, 0, "Une digression sans rapport.", 0.1f),
                unExtrait(document, 1, "La réponse est quarante-deux.", 1f),
                unExtrait(document, 2, "Un passage à peu près sur le sujet.", 0.6f)));

        List<ChunkMatchView> resultats = queryBus.ask(new SearchChunks("Quelle est la réponse ?", alice));

        assertThat(resultats).extracting(ChunkMatchView::text).startsWith("La réponse est quarante-deux.");
    }

    @Test
    void rend_pour_chaque_extrait_son_contenu_son_document_sa_position_et_son_score() {
        UUID alice = unCompte("bruno@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                2,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.unVecteurProche(1f),
                Instant.now())));

        List<ChunkMatchView> resultats = queryBus.ask(new SearchChunks("Une question", alice));

        assertThat(resultats).singleElement().satisfies(resultat -> {
            assertThat(resultat.documentId()).isEqualTo(document.getId());
            assertThat(resultat.filename()).isEqualTo("rapport-annuel.md");
            assertThat(resultat.position()).isEqualTo(2);
            assertThat(resultat.heading()).isEqualTo("Introduction");
            assertThat(resultat.text()).isEqualTo("Le corps de l'extrait.");
            assertThat(resultat.similarity()).isGreaterThan(0.99d);
        });
    }

    @Test
    void rend_une_liste_vide_pour_une_base_de_connaissance_vide() {
        UUID alice = unCompte("clara@exemple.fr");

        assertThat(queryBus.ask(new SearchChunks("Une question", alice))).isEmpty();
    }

    @Test
    void ne_rend_pas_les_extraits_d_un_autre_compte() {
        UUID alice = unCompte("diane@exemple.fr");
        UUID bob = unCompte("edgar@exemple.fr");
        textChunkRepository.saveAll(List.of(
                unExtrait(unDocumentDepose(alice, "a-elle.md"), 0, "Le sien.", 0.5f),
                unExtrait(unDocumentDepose(bob, "a-lui.md"), 0, "Celui de Bob.", 1f)));

        assertThat(queryBus.ask(new SearchChunks("Une question", alice)))
                .extracting(ChunkMatchView::text)
                .containsExactly("Le sien.");
    }

    @Test
    void ne_rend_jamais_plus_de_huit_extraits() {
        UUID alice = unCompte("fatou@exemple.fr");
        Document document = unDocumentDepose(alice, "long.md");
        textChunkRepository.saveAll(IntStream.range(0, 12)
                .mapToObj(position -> unExtrait(document, position, "Extrait " + position, 0.5f))
                .toList());

        assertThat(queryBus.ask(new SearchChunks("Une question", alice))).hasSize(8);
    }

    @Test
    void refuse_une_question_vide() {
        UUID alice = unCompte("gaspard@exemple.fr");

        assertThatThrownBy(() -> queryBus.ask(new SearchChunks("   ", alice)))
                .isInstanceOf(InvalidQuestionException.class)
                .hasMessage("La question ne peut pas être vide.");
    }

    @Test
    void vectorise_la_question_telle_qu_elle_a_ete_posee() {
        UUID alice = unCompte("helena@exemple.fr");

        queryBus.ask(new SearchChunks("  Qui a signé le rapport ?  ", alice));

        assertThat(recordingEmbeddingPort.textesRecus()).containsExactly("Qui a signé le rapport ?");
    }

    private static TextChunk unExtrait(Document document, int position, String corps, float proximite) {
        return TextChunk.of(
                document.getId(),
                position,
                new Chunk("Titre", corps),
                KnowledgeFixture.unVecteurProche(proximite),
                Instant.now());
    }

    private UUID unCompte(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private Document unDocumentDepose(UUID proprietaire, String nom) {
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, nom, DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}
