package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.application.command.UploadDocument;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
@ExtendWith(OutputCaptureExtension.class)
class KnowledgeEventListenerTest {

    private static final Duration DELAI = Duration.ofSeconds(20);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private RecordingEmbeddingPort embeddingPort;

    @Autowired
    private DocumentStorage documentStorage;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    private final List<String> comptesCrees = new ArrayList<>();

    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        // La clé étrangère en cascade emporte les documents, leurs textes et leurs extraits
        // avec le compte ; le disque, lui, ne participe à aucune transaction (ADR-0020).
        comptesCrees.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        comptesCrees.clear();
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
        embeddingPort.clear();
    }

    @Test
    void declare_la_queue_du_contexte() {
        assertThat(amqpAdmin.getQueueInfo("domain.knowledge.events")).isNotNull();
    }

    @Test
    void le_role_worker_demarre_sans_serveur_http_ni_filtre_de_securite() {
        assertThat(applicationContext).isNotInstanceOf(WebApplicationContext.class);
        assertThat(applicationContext.getBeanNamesForType(SecurityFilterChain.class))
                .isEmpty();
        assertThat(applicationContext.getBeanNamesForType(KnowledgeEventListener.class))
                .hasSize(1);
    }

    @Test
    void indexe_le_document_dont_le_depot_est_annonce_et_le_declare_pret() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);

        publie(document);

        await().atMost(DELAI).untilAsserted(() -> {
            assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                    .get()
                    .satisfies(texte -> assertThat(texte.getBlocks()).isNotEmpty());
            assertThat(textChunkRepository.findByDocumentId(document.getId())).isNotEmpty();
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.READY);
        });
    }

    @Test
    void marque_le_document_en_echec_quand_l_extraction_refuse() {
        Document document = unDocumentDepose("scan.pdf", Fixtures.NUMERISE_PDF);

        publie(document);

        await().atMost(DELAI).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.FAILED);
            assertThat(motifDe(document)).contains("pas de texte exploitable");
        });
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void n_expose_pas_le_message_d_une_panne_technique() {
        Document document = unDocumentDepose("notes.txt", Fixtures.BRUT_TXT);
        documentStorage.delete(document.getId());

        publie(document);

        await().atMost(DELAI).untilAsserted(() -> assertThat(motifDe(document)).contains("n'a pas pu être lu"));
    }

    @Test
    void ne_double_ni_le_texte_ni_les_extraits_quand_l_evenement_est_livre_deux_fois() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);
        publie(document);
        await().atMost(DELAI).untilAsserted(() -> assertThat(statutDe(document)).isEqualTo(DocumentStatus.READY));
        int extraits = textChunkRepository.findByDocumentId(document.getId()).size();

        publie(document);

        await().during(Duration.ofSeconds(2)).atMost(DELAI).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.READY);
            assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                    .isPresent();
            assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(extraits);
        });
    }

    @Test
    void marque_le_document_en_echec_quand_la_vectorisation_ne_repond_pas() {
        Document document = unDocumentDepose("structure.md", Fixtures.STRUCTURE_MD);
        embeddingPort.tombeEnPanne();

        publie(document);

        await().atMost(DELAI).untilAsserted(() -> {
            assertThat(statutDe(document)).isEqualTo(DocumentStatus.FAILED);
            assertThat(motifDe(document)).contains("vectorisation");
        });
        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();
    }

    @Test
    void rejette_un_evenement_non_declare_sans_le_retraiter(CapturedOutput sortie) throws InterruptedException {
        UUID document = UUID.randomUUID();
        Message message = rabbitTemplate
                .getMessageConverter()
                .toMessage(
                        new DocumentUploaded(document, UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z")),
                        new MessageProperties());
        message.getMessageProperties().setHeader("__TypeId__", "knowledge.inconnu.survenu");

        // La queue est liée sur `knowledge.#` : elle reçoit tout le contexte, et c'est
        // l'en-tête de type qui est jugé, pas la clé de routage.
        rabbitTemplate.send("domain.events", "knowledge.inconnu.survenu", message);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(sortie).contains("knowledge.inconnu.survenu"));

        int occurrences = occurrencesDe("knowledge.inconnu.survenu", sortie);
        Thread.sleep(1000);
        assertThat(occurrencesDe("knowledge.inconnu.survenu", sortie)).isEqualTo(occurrences);
    }

    private void publie(Document document) {
        rabbitTemplate.convertAndSend(
                "domain.events",
                "knowledge.document.uploaded",
                new DocumentUploaded(document.getId(), document.getOwnerId(), Instant.now()));
    }

    private Document unDocumentDepose(String filename, String fixture) {
        String email = UUID.randomUUID() + "@exemple.fr";
        UUID proprietaire = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        comptesCrees.add(email);
        byte[] contenu = Fixtures.lire(fixture);
        commandBus.dispatch(new UploadDocument(proprietaire, filename, contenu));
        return documentRepository
                .findByOwnerIdAndChecksum(proprietaire, Checksum.of(contenu))
                .orElseThrow();
    }

    private DocumentStatus statutDe(Document document) {
        return relis(document).getStatus();
    }

    private String motifDe(Document document) {
        return relis(document).getErrorMessage();
    }

    private Document relis(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }

    private int occurrencesDe(String motif, CapturedOutput sortie) {
        int total = 0;
        int index = sortie.getOut().indexOf(motif);
        while (index >= 0) {
            total++;
            index = sortie.getOut().indexOf(motif, index + motif.length());
        }
        return total;
    }
}
