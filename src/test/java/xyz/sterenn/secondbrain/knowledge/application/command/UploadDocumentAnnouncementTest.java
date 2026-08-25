package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;

/**
 * Le premier scénario du socle : un dépôt réussi est annoncé, par le chemin réel — le bus,
 * sa transaction, son commit.
 *
 * <p>Pas de {@code @Transactional} : l'annonce ne part qu'au commit, une transaction de test
 * l'empêcherait de partir. Le compte et le document sont donc réellement écrits, et effacés
 * en {@code @AfterEach} — la clé étrangère en cascade emporte le document avec le compte,
 * le disque se vide à part.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
class UploadDocumentAnnouncementTest {

    private static final String EMAIL = "gaston@exemple.fr";
    private static final String OBSERVATION = "test.observation.depot";
    private static final byte[] CONTENU = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    private UUID compte;

    @BeforeEach
    void prepare_un_compte_et_une_queue_d_observation() {
        recordingNotificationSender.clear();
        compte = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, "chevalpile42");
        amqpAdmin.declareQueue(new Queue(OBSERVATION));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION,
                Binding.DestinationType.QUEUE,
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.DocumentUploaded",
                null));
    }

    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        amqpAdmin.deleteQueue(OBSERVATION);
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    @Test
    void annonce_le_document_depose_une_fois_le_depot_commite() {
        commandBus.dispatch(new UploadDocument(compte, "rapport.txt", CONTENU));

        Object recu = rabbitTemplate.receiveAndConvert(
                OBSERVATION, Duration.ofSeconds(5).toMillis());

        Document document = documentRepository
                .findByOwnerIdAndChecksum(compte, Checksum.of(CONTENU))
                .orElseThrow();
        assertThat(recu).isInstanceOf(DocumentUploaded.class);
        DocumentUploaded evenement = (DocumentUploaded) recu;
        assertThat(evenement.documentId()).isEqualTo(document.getId());
        assertThat(evenement.ownerId()).isEqualTo(compte);
        assertThat(evenement.occurredAt()).isNotNull();
    }
}
