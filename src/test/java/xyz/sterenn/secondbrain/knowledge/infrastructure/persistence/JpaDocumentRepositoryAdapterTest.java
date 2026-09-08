package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

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

    private static final DriveProvenance PROVENANCE = new DriveProvenance(
            "1aBcD", "https://drive.google.com/file/d/1aBcD/view", Instant.parse("2026-09-08T10:15:30Z"));

    private UUID existingAccount(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private static Document imported(UUID ownerId, String filename, String content, String driveFileId) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return Document.importedFromDrive(
                ownerId,
                filename,
                DocumentFormat.fromFilename(filename),
                Checksum.of(bytes),
                bytes.length,
                new DriveProvenance(driveFileId, PROVENANCE.webViewLink(), PROVENANCE.modifiedTime()));
    }

    private static Document document(UUID ownerId, String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return Document.upload(
                ownerId, filename, DocumentFormat.fromFilename(filename), Checksum.of(bytes), bytes.length);
    }

    @Test
    void persists_a_document_awaiting_processing() {
        UUID ownerId = existingAccount("alice@exemple.fr");

        Document saved = documentRepository.save(document(ownerId, "rapport.pdf", "contenu"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void projects_the_checksum_onto_a_text_column() {
        UUID ownerId = existingAccount("bob@exemple.fr");
        Document saved = documentRepository.save(document(ownerId, "notes.md", "contenu"));

        String column = jdbcTemplate.queryForObject(
                "SELECT checksum FROM knowledge_documents WHERE id = ?", String.class, saved.getId());

        assertThat(column)
                .isEqualTo(
                        Checksum.of("contenu".getBytes(StandardCharsets.UTF_8)).value());
    }

    @Test
    void finds_a_document_by_owner_and_checksum() {
        UUID ownerId = existingAccount("carole@exemple.fr");
        documentRepository.save(document(ownerId, "rapport.pdf", "contenu"));

        assertThat(documentRepository.findByOwnerIdAndChecksum(
                        ownerId, Checksum.of("contenu".getBytes(StandardCharsets.UTF_8))))
                .isPresent();
    }

    @Test
    void does_not_find_the_document_of_another_account_by_its_checksum() {
        UUID alice = existingAccount("alice2@exemple.fr");
        UUID bob = existingAccount("bob2@exemple.fr");
        documentRepository.save(document(alice, "rapport.pdf", "contenu"));

        assertThat(documentRepository.findByOwnerIdAndChecksum(
                        bob, Checksum.of("contenu".getBytes(StandardCharsets.UTF_8))))
                .isEmpty();
    }

    @Test
    void lets_two_accounts_upload_the_same_content() {
        UUID alice = existingAccount("alice3@exemple.fr");
        UUID bob = existingAccount("bob3@exemple.fr");

        documentRepository.save(document(alice, "rapport.pdf", "contenu"));

        assertThat(documentRepository
                        .save(document(bob, "rapport.pdf", "contenu"))
                        .getId())
                .isNotNull();
    }

    @Test
    void rejects_the_same_content_twice_for_the_same_account() {
        UUID ownerId = existingAccount("david@exemple.fr");
        documentRepository.save(document(ownerId, "rapport.pdf", "contenu"));

        assertThatThrownBy(() -> documentRepository.save(document(ownerId, "copie.pdf", "contenu")))
                .isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void returns_the_documents_of_an_account_from_the_most_recent_to_the_oldest() {
        UUID ownerId = existingAccount("eve@exemple.fr");
        documentRepository.save(document(ownerId, "ancien.pdf", "premier"));
        documentRepository.save(document(ownerId, "recent.pdf", "second"));

        assertThat(documentRepository.findAllByOwnerId(ownerId))
                .extracting(Document::getFilename)
                .containsExactly("recent.pdf", "ancien.pdf");
    }

    @Test
    void returns_only_the_documents_of_the_requested_account() {
        UUID alice = existingAccount("alice4@exemple.fr");
        UUID bob = existingAccount("bob4@exemple.fr");
        documentRepository.save(document(alice, "chez-alice.pdf", "premier"));
        documentRepository.save(document(bob, "chez-bob.pdf", "second"));

        assertThat(documentRepository.findAllByOwnerId(alice))
                .extracting(Document::getFilename)
                .containsExactly("chez-alice.pdf");
    }

    @Test
    void does_not_find_by_id_the_document_of_another_account() {
        UUID alice = existingAccount("alice5@exemple.fr");
        UUID bob = existingAccount("bob5@exemple.fr");
        UUID document = documentRepository
                .save(document(alice, "rapport.pdf", "contenu"))
                .getId();

        assertThat(documentRepository.findByIdAndOwnerId(document, bob)).isEmpty();
    }

    @Test
    void deletes_a_document() {
        UUID ownerId = existingAccount("frank@exemple.fr");
        Document saved = documentRepository.save(document(ownerId, "rapport.pdf", "contenu"));

        documentRepository.delete(saved);

        assertThat(documentRepository.findAllByOwnerId(ownerId)).isEmpty();
    }

    @Test
    void keeps_the_failure_status_and_its_reason() {
        UUID ownerId = existingAccount("denis@exemple.fr");
        Document saved = documentRepository.save(document(ownerId, "scan.pdf", "contenu"));

        saved.markProcessingFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(saved);

        assertThat(documentRepository.findByIdAndOwnerId(saved.getId(), ownerId))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(reloaded.getErrorMessage())
                            .isEqualTo("Ce document ne contient pas de texte exploitable.");
                });
    }

    @Test
    void lets_every_manually_uploaded_document_share_an_absent_drive_file() {
        UUID ownerId = existingAccount("gaelle@exemple.fr");

        documentRepository.save(document(ownerId, "premier.pdf", "premier"));
        documentRepository.save(document(ownerId, "second.pdf", "second"));
        documentRepository.save(document(ownerId, "troisieme.pdf", "troisieme"));

        assertThat(documentRepository.findAllByOwnerId(ownerId)).hasSize(3);
    }

    @Test
    void finds_an_imported_document_by_its_drive_file() {
        UUID ownerId = existingAccount("helene@exemple.fr");
        documentRepository.save(imported(ownerId, "rapport.pdf", "contenu", "1aBcD"));

        assertThat(documentRepository.findByOwnerIdAndDriveFileId(ownerId, "1aBcD"))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
                    assertThat(reloaded.getDriveProvenance()).contains(PROVENANCE);
                });
    }

    @Test
    void does_not_find_the_imported_document_of_another_account_by_its_drive_file() {
        UUID alice = existingAccount("alice6@exemple.fr");
        UUID bob = existingAccount("bob6@exemple.fr");
        documentRepository.save(imported(alice, "rapport.pdf", "contenu", "1aBcD"));

        assertThat(documentRepository.findByOwnerIdAndDriveFileId(bob, "1aBcD")).isEmpty();
    }

    @Test
    void knows_no_document_behind_a_drive_file_that_brought_none() {
        UUID ownerId = existingAccount("irene@exemple.fr");
        documentRepository.save(document(ownerId, "rapport.pdf", "contenu"));

        assertThat(documentRepository.findByOwnerIdAndDriveFileId(ownerId, "1aBcD"))
                .isEmpty();
    }

    @Test
    void rejects_the_same_drive_file_twice_for_the_same_account() {
        UUID ownerId = existingAccount("julien@exemple.fr");
        documentRepository.save(imported(ownerId, "rapport.pdf", "premier", "1aBcD"));

        assertThatThrownBy(() -> documentRepository.save(imported(ownerId, "copie.pdf", "second", "1aBcD")))
                .isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void an_uploaded_document_is_manual_until_a_drive_file_brings_it() {
        UUID ownerId = existingAccount("karim@exemple.fr");

        Document saved = documentRepository.save(document(ownerId, "rapport.pdf", "contenu"));

        assertThat(saved.getSource()).isEqualTo(DocumentSource.MANUAL);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT drive_file_id FROM knowledge_documents WHERE id = ?", String.class, saved.getId()))
                .isNull();
    }
}
