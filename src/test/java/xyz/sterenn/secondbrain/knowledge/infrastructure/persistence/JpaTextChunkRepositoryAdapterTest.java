package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * On teste le <strong>port</strong>, jamais l'adapter. Le montage du propriétaire est celui
 * de {@link JpaTextExtractionRepositoryAdapterTest} — la clé étrangère de
 * {@code knowledge_documents} traverse les deux contextes bornés.
 *
 * <p>Le vecteur n'est pas un détail dans ces assertions : c'est la seule vérification que
 * {@code float[]} atterrit bien dans une colonne {@code vector(1024)} et en revient
 * identique. Un test unitaire de l'entité n'apprendrait rien là-dessus.
 *
 * <p>La cascade depuis {@code knowledge_documents}, elle, est vérifiée par
 * {@code DeleteDocumentCascadeTest}, non transactionnel : ici, Hibernate rendrait les extraits
 * depuis son cache de premier niveau sans jamais interroger la base.
 */
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

    private static TextChunk unExtrait(Document document, int position, String titre, String corps) {
        return TextChunk.of(
                document.getId(), position, new Chunk(titre, corps), KnowledgeFixture.unVecteur(0.5f), Instant.now());
    }

    private Document unDocumentDepose(String email) {
        UUID proprietaire = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(proprietaire, "notes.md", DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}
