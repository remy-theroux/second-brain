package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindDocumentControllerTest {

    private static final String MOT_DE_PASSE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String bucketDesOriginaux;

    private UUID alice;
    private String jetonAlice;

    @BeforeEach
    void prepare_un_compte_connecte() {
        recordingNotificationSender.clear();
        alice = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);
        jetonAlice = KnowledgeFixture.jeton(accessTokenIssuer, alice);
    }

    @AfterEach
    void efface_les_originaux() {
        KnowledgeFixture.videLesOriginaux(s3Client, bucketDesOriginaux);
    }

    @Test
    void rend_le_document_et_le_texte_qui_en_a_ete_extrait() throws Exception {
        Document document = depose(jetonAlice, alice, "structure.md", Fixtures.STRUCTURE_MD);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), alice));

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("structure.md"))
                .andExpect(jsonPath("$.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.type").value("TEXTUAL"))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.extraction.extractedAt").isNotEmpty())
                .andExpect(jsonPath("$.extraction.characterCount").isNumber())
                .andExpect(jsonPath("$.extraction.blocks[*].heading", hasItem("Journal de bord")))
                .andExpect(jsonPath("$.extraction.blocks[0].text").isNotEmpty());
    }

    @Test
    void annonce_la_typologie_sans_extraction_tant_que_le_traitement_n_a_pas_eu_lieu() throws Exception {
        Document document = depose(jetonAlice, alice, "notes.txt", Fixtures.BRUT_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("TEXTUAL"))
                .andExpect(jsonPath("$.extraction").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void rend_le_motif_d_un_document_en_echec_et_aucune_extraction() throws Exception {
        Document document = depose(jetonAlice, alice, "scan.txt", Fixtures.BRUT_TXT);
        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Ce document ne contient pas de texte exploitable."))
                .andExpect(jsonPath("$.extraction").doesNotExist());
    }

    @Test
    void rend_introuvable_un_identifiant_inconnu() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rend_introuvable_le_document_d_un_autre_compte() throws Exception {
        UUID bob = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);
        Document chezBob =
                depose(KnowledgeFixture.jeton(accessTokenIssuer, bob), bob, "chez-bob.txt", Fixtures.BRUT_TXT);

        mockMvc.perform(get("/api/documents/" + chezBob.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse_la_lecture_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private Document depose(String jeton, UUID proprietaire, String nom, String fixture) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", nom, "application/octet-stream", Fixtures.lire(fixture)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(proprietaire).stream()
                .filter(document -> document.getFilename().equals(nom))
                .findFirst()
                .orElseThrow();
    }
}
