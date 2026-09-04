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
    void conserve_les_extraits_dans_l_ordre_avec_leur_titre_et_leur_corps() {
        Document document = unDocumentDepose("mona@exemple.fr");

        textChunkRepository.saveAll(List.of(
                unExtrait(document, 0, "Introduction", "Le premier extrait."),
                unExtrait(document, 1, "Introduction", "Le deuxième extrait."),
                unExtrait(document, 2, "", "Le troisième, sans titre.")));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getPosition)
                .containsExactly(0, 1, 2);
        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .extracting(TextChunk::getHeading)
                .containsExactly("Introduction", "Introduction", "");
    }

    @Test
    void rend_le_vecteur_tel_qu_il_a_ete_range() {
        Document document = unDocumentDepose("nadir@exemple.fr");
        Embedding vecteur = KnowledgeFixture.unVecteur(0.25f);

        textChunkRepository.saveAll(
                List.of(TextChunk.of(document.getId(), 0, new Chunk("Titre", "Un corps."), vecteur, Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.getEmbedding()).isEqualTo(vecteur));
    }

    @Test
    void rend_l_extrait_du_domaine_tel_qu_il_a_ete_range() {
        Document document = unDocumentDepose("olga@exemple.fr");
        Chunk extrait = new Chunk("Introduction", "Un corps bien à lui.");

        textChunkRepository.saveAll(
                List.of(TextChunk.of(document.getId(), 0, extrait, KnowledgeFixture.unVecteur(0.5f), Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.chunk()).isEqualTo(extrait));
    }

    @Test
    void conserve_un_extrait_bien_plus_long_que_255_caracteres() {
        Document document = unDocumentDepose("pierre@exemple.fr");
        String tresLong = "Un corps qui déborde largement d'une colonne de 255 caractères. ".repeat(50);

        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(), 0, new Chunk("", tresLong), KnowledgeFixture.unVecteur(0.1f), Instant.now())));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.getText()).isEqualTo(tresLong.strip()));
    }

    @Test
    void efface_les_extraits_d_un_document() {
        Document document = unDocumentDepose("quentin@exemple.fr");
        textChunkRepository.saveAll(List.of(unExtrait(document, 0, "Titre", "Un corps.")));

        textChunkRepository.deleteByDocumentId(document.getId());

        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void un_second_jeu_d_extraits_remplace_le_premier_apres_effacement() {
        // AMQP livre au moins une fois, et (document_id, chunk_position) est UNIQUE : sans
        // l'effacement préalable, la seconde écriture se heurterait à la contrainte.
        Document document = unDocumentDepose("rosa@exemple.fr");
        textChunkRepository.saveAll(List.of(unExtrait(document, 0, "Titre", "Première version.")));

        textChunkRepository.deleteByDocumentId(document.getId());
        textChunkRepository.saveAll(List.of(unExtrait(document, 0, "Titre", "Deuxième version.")));

        assertThat(textChunkRepository.findByDocumentId(document.getId()))
                .singleElement()
                .satisfies(relu -> assertThat(relu.getText()).isEqualTo("Deuxième version."));
    }

    @Test
    void reste_muet_quand_aucun_extrait_n_a_ete_range() {
        assertThat(textChunkRepository.findByDocumentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rend_les_extraits_du_plus_proche_au_plus_lointain() {
        UUID alice = unCompte("sylvie@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport.md");
        textChunkRepository.saveAll(List.of(
                unExtraitOriente(document, 0, "Le plus lointain.", 0.1f),
                unExtraitOriente(document, 1, "Le plus proche.", 1f),
                unExtraitOriente(document, 2, "L'intermédiaire.", 0.6f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .extracting(match -> match.chunk().text())
                .containsExactly("Le plus proche.", "L'intermédiaire.", "Le plus lointain.");
    }

    @Test
    void rend_le_nom_du_document_la_position_et_le_titre_de_chaque_extrait() {
        UUID alice = unCompte("thomas@exemple.fr");
        Document document = unDocumentDepose(alice, "rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                3,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.unVecteurProche(1f),
                Instant.now())));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.documentId()).isEqualTo(document.getId());
                    assertThat(match.filename()).isEqualTo("rapport-annuel.md");
                    assertThat(match.position()).isEqualTo(3);
                    assertThat(match.chunk()).isEqualTo(new Chunk("Introduction", "Le corps de l'extrait."));
                });
    }

    @Test
    void rend_une_similarite_de_un_pour_un_extrait_dont_le_vecteur_est_celui_de_la_question() {
        UUID alice = unCompte("ursula@exemple.fr");
        Document document = unDocumentDepose(alice, "notes.md");
        textChunkRepository.saveAll(List.of(unExtraitOriente(document, 0, "Identique.", 1f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .singleElement()
                .satisfies(match -> assertThat(match.similarity()).isCloseTo(1d, within(0.0001d)));
    }

    @Test
    void ne_rend_pas_les_extraits_d_un_autre_compte() {
        UUID alice = unCompte("valentine@exemple.fr");
        UUID bob = unCompte("walid@exemple.fr");
        Document leSien = unDocumentDepose(alice, "a-elle.md");
        Document celuiDeBob = unDocumentDepose(bob, "a-lui.md");
        textChunkRepository.saveAll(List.of(
                unExtraitOriente(leSien, 0, "Le sien.", 0.5f), unExtraitOriente(celuiDeBob, 0, "Celui de Bob.", 1f)));

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .extracting(match -> match.chunk().text())
                .containsExactly("Le sien.");
    }

    @Test
    void plafonne_le_nombre_d_extraits_rendus() {
        UUID alice = unCompte("xavier@exemple.fr");
        Document document = unDocumentDepose(alice, "long.md");
        textChunkRepository.saveAll(IntStream.range(0, 12)
                .mapToObj(position -> unExtraitOriente(document, position, "Extrait " + position, 0.5f))
                .toList());

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .hasSize(8);
    }

    @Test
    void reste_muet_quand_le_compte_ne_porte_aucun_extrait() {
        UUID alice = unCompte("yasmine@exemple.fr");

        assertThat(textChunkRepository.findNearest(alice, KnowledgeFixture.uneQuestion(), 8))
                .isEmpty();
    }

    private static TextChunk unExtraitOriente(Document document, int position, String corps, float proximite) {
        return TextChunk.of(
                document.getId(),
                position,
                new Chunk("Titre", corps),
                KnowledgeFixture.unVecteurProche(proximite),
                Instant.now());
    }

    private static TextChunk unExtrait(Document document, int position, String titre, String corps) {
        return TextChunk.of(
                document.getId(), position, new Chunk(titre, corps), KnowledgeFixture.unVecteur(0.5f), Instant.now());
    }

    private UUID unCompte(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private Document unDocumentDepose(UUID proprietaire, String nom) {
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, nom, DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }

    private Document unDocumentDepose(String email) {
        return unDocumentDepose(unCompte(email), "notes.md");
    }
}
