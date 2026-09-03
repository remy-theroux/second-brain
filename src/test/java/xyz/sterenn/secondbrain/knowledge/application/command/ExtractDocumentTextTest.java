package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * {@code @Transactional} : la base est annulée après chaque test. Le stockage objet, lui, ne
 * l'est pas — d'où le nettoyage explicite en {@code @AfterEach}.
 *
 * <p>Le dépôt passe par le bus, comme en production : c'est lui qui écrit l'original, sans
 * quoi l'extraction n'aurait rien à lire. Le compte est créé par le port plutôt que par
 * l'inscription complète : ni le dépôt ni l'extraction ne regardent s'il est vérifié.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ExtractDocumentTextTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String bucketDesOriginaux;

    @AfterEach
    void nettoieLesOriginaux() {
        KnowledgeFixture.videLesOriginaux(s3Client, bucketDesOriginaux);
    }

    @Test
    void range_le_texte_extrait_et_marque_le_document_extrait() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(texte -> assertThat(texte.getBlocks())
                        .extracting(TextBlock::getHeading)
                        .contains("Journal de bord"));
        assertThat(relis(document).getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
    }

    @Test
    void une_seconde_extraction_remplace_la_premiere_sans_la_doubler() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();
    }

    @Test
    void choisit_l_extracteur_du_format_du_document() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(texte -> assertThat(texte.getBlocks()).hasSize(1));
    }

    @Test
    void refuse_un_document_qui_n_appartient_pas_au_demandeur() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);

        // Dernier appel du test : le refus marque la transaction englobante rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(new ExtractDocumentText(document.getId(), UUID.randomUUID())));
    }

    @Test
    void laisse_remonter_le_refus_d_un_document_inexploitable() {
        Document document = unDocumentDepose("scan.pdf", Fixtures.NUMERISE_PDF);

        // Dernier appel du test, même raison.
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(
                        () -> commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId())));
    }

    private Document unDocumentDepose(String filename, String fixture) {
        UUID proprietaire = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] contenu = Fixtures.lire(fixture);
        commandBus.dispatch(new UploadDocument(proprietaire, filename, contenu));
        return documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
    }

    private Document relis(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }
}
