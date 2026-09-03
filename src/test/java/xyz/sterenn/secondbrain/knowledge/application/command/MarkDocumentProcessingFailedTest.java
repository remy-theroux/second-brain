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
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MarkDocumentProcessingFailedTest {

    private static final String MOTIF = "Ce document ne contient pas de texte exploitable.";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

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
    void marque_le_document_en_echec_avec_son_motif() {
        Document document = unDocumentDepose();

        commandBus.dispatch(new MarkDocumentProcessingFailed(document.getId(), document.getOwnerId(), MOTIF));

        assertThat(documentRepository
                        .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                        .orElseThrow())
                .satisfies(relu -> {
                    assertThat(relu.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(relu.getErrorMessage()).isEqualTo(MOTIF);
                });
    }

    @Test
    void refuse_de_marquer_un_document_disparu() {
        // Dernier appel du test : le refus marque la transaction englobante rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(
                        new MarkDocumentProcessingFailed(UUID.randomUUID(), UUID.randomUUID(), MOTIF)));
    }

    private Document unDocumentDepose() {
        UUID proprietaire = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] contenu = Fixtures.lire(Fixtures.BRUT_TXT);
        commandBus.dispatch(new UploadDocument(proprietaire, "notes.txt", contenu));
        return documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
    }
}
