package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Pas de {@code @Transactional} : dans une transaction, Hibernate rendrait le
 * {@code TextExtraction} depuis son cache de premier niveau sans interroger la base, et le test
 * passerait au vert quelle que soit la migration. D'où le nettoyage en {@code @AfterEach}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeleteDocumentCascadeTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String bucketDesOriginaux;

    private final List<String> comptesCrees = new ArrayList<>();

    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        comptesCrees.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        comptesCrees.clear();
        KnowledgeFixture.videLesOriginaux(s3Client, bucketDesOriginaux);
    }

    @Test
    void la_suppression_d_un_document_emporte_son_texte_extrait() {
        Document document = unDocumentDepose();
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_text_blocks", Integer.class))
                .isZero();
    }

    @Test
    void la_suppression_d_un_document_emporte_ses_extraits() {
        Document document = unDocumentDepose();
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                0,
                new Chunk("Titre", "Un corps."),
                KnowledgeFixture.unVecteur(0.5f),
                Instant.now())));
        assertThat(textChunkRepository.findByDocumentId(document.getId())).isNotEmpty();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
    }

    private Document unDocumentDepose() {
        String email = UUID.randomUUID() + "@exemple.fr";
        UUID proprietaire = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        comptesCrees.add(email);
        byte[] contenu = Fixtures.lire(Fixtures.BRUT_TXT);
        commandBus.dispatch(new UploadDocument(proprietaire, "notes.txt", contenu));
        return documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
    }
}
