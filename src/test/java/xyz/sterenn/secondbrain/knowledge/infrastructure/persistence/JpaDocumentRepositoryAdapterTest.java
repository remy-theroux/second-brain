package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Le test injecte le <em>port</em>, pas l'adapter : c'est le contrat du domaine qui est
 * vérifié. {@code @Transactional} fait rouler chaque test en arrière.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaDocumentRepositoryAdapterTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID compteExistant(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private static Document document(UUID proprietaire, String nom, String contenu) {
        byte[] octets = contenu.getBytes(StandardCharsets.UTF_8);
        return Document.upload(proprietaire, nom, DocumentFormat.fromFilename(nom), Checksum.of(octets), octets.length);
    }

    @Test
    void persiste_un_document_en_attente_de_traitement() {
        UUID proprietaire = compteExistant("alice@exemple.fr");

        Document enregistre = documentRepository.save(document(proprietaire, "rapport.pdf", "contenu"));

        assertThat(enregistre.getId()).isNotNull();
        assertThat(enregistre.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(enregistre.getCreatedAt()).isNotNull();
    }

    @Test
    void projette_l_empreinte_sur_une_colonne_texte() {
        // Le converter est appliqué par autoApply, sans qu'aucune ligne de code le nomme :
        // seule la lecture du contenu réel de la colonne le prouve.
        UUID proprietaire = compteExistant("bob@exemple.fr");
        Document enregistre = documentRepository.save(document(proprietaire, "notes.md", "contenu"));

        String colonne = jdbcTemplate.queryForObject(
                "SELECT checksum FROM knowledge_documents WHERE id = ?", String.class, enregistre.getId());

        assertThat(colonne)
                .isEqualTo(
                        Checksum.of("contenu".getBytes(StandardCharsets.UTF_8)).value());
    }

    @Test
    void retrouve_un_document_par_proprietaire_et_empreinte() {
        UUID proprietaire = compteExistant("carole@exemple.fr");
        documentRepository.save(document(proprietaire, "rapport.pdf", "contenu"));

        assertThat(documentRepository.findByOwnerIdAndChecksum(
                        proprietaire, Checksum.of("contenu".getBytes(StandardCharsets.UTF_8))))
                .isPresent();
    }

    @Test
    void ne_retrouve_pas_le_document_d_un_autre_compte_par_son_empreinte() {
        UUID alice = compteExistant("alice2@exemple.fr");
        UUID bob = compteExistant("bob2@exemple.fr");
        documentRepository.save(document(alice, "rapport.pdf", "contenu"));

        assertThat(documentRepository.findByOwnerIdAndChecksum(
                        bob, Checksum.of("contenu".getBytes(StandardCharsets.UTF_8))))
                .isEmpty();
    }

    @Test
    void laisse_deux_comptes_deposer_le_meme_contenu() {
        // L'unicité porte sur (propriétaire, empreinte) : chacun a sa base de connaissance.
        UUID alice = compteExistant("alice3@exemple.fr");
        UUID bob = compteExistant("bob3@exemple.fr");

        documentRepository.save(document(alice, "rapport.pdf", "contenu"));

        assertThat(documentRepository
                        .save(document(bob, "rapport.pdf", "contenu"))
                        .getId())
                .isNotNull();
    }

    @Test
    void refuse_deux_fois_le_meme_contenu_pour_un_meme_compte() {
        // Le filet de la contrainte d'unicité, que le handler n'atteint qu'en cas de dépôts
        // simultanés : ici on l'exerce directement, sans passer par lui.
        UUID proprietaire = compteExistant("david@exemple.fr");
        documentRepository.save(document(proprietaire, "rapport.pdf", "contenu"));

        assertThatThrownBy(() -> documentRepository.save(document(proprietaire, "copie.pdf", "contenu")))
                .isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void rend_les_documents_d_un_compte_du_plus_recent_au_plus_ancien() {
        UUID proprietaire = compteExistant("eve@exemple.fr");
        documentRepository.save(document(proprietaire, "ancien.pdf", "premier"));
        documentRepository.save(document(proprietaire, "recent.pdf", "second"));

        assertThat(documentRepository.findAllByOwnerId(proprietaire))
                .extracting(Document::getFilename)
                .containsExactly("recent.pdf", "ancien.pdf");
    }

    @Test
    void ne_rend_que_les_documents_du_compte_demande() {
        UUID alice = compteExistant("alice4@exemple.fr");
        UUID bob = compteExistant("bob4@exemple.fr");
        documentRepository.save(document(alice, "chez-alice.pdf", "premier"));
        documentRepository.save(document(bob, "chez-bob.pdf", "second"));

        assertThat(documentRepository.findAllByOwnerId(alice))
                .extracting(Document::getFilename)
                .containsExactly("chez-alice.pdf");
    }

    @Test
    void ne_retrouve_pas_par_identifiant_le_document_d_un_autre_compte() {
        UUID alice = compteExistant("alice5@exemple.fr");
        UUID bob = compteExistant("bob5@exemple.fr");
        UUID document = documentRepository
                .save(document(alice, "rapport.pdf", "contenu"))
                .getId();

        assertThat(documentRepository.findByIdAndOwnerId(document, bob)).isEmpty();
    }

    @Test
    void efface_un_document() {
        UUID proprietaire = compteExistant("frank@exemple.fr");
        Document enregistre = documentRepository.save(document(proprietaire, "rapport.pdf", "contenu"));

        documentRepository.delete(enregistre);

        assertThat(documentRepository.findAllByOwnerId(proprietaire)).isEmpty();
    }

    @Test
    void conserve_le_statut_d_echec_et_son_motif() {
        UUID proprietaire = compteExistant("denis@exemple.fr");
        Document enregistre = documentRepository.save(document(proprietaire, "scan.pdf", "contenu"));

        enregistre.markExtractionFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(enregistre);

        assertThat(documentRepository.findByIdAndOwnerId(enregistre.getId(), proprietaire))
                .get()
                .satisfies(relu -> {
                    assertThat(relu.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(relu.getErrorMessage()).isEqualTo("Ce document ne contient pas de texte exploitable.");
                });
    }
}
