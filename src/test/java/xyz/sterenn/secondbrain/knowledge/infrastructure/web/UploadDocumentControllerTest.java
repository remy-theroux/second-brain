package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Un refus est toujours le <em>dernier</em> appel HTTP de son test : l'exception marque la
 * transaction englobante « rollback-only », et un second appel derrière échouerait sur une
 * {@code UnexpectedRollbackException}. Ce qui reste à vérifier après un refus se lit par le port.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UploadDocumentControllerTest {

    private static final String MOT_DE_PASSE = "chevalpile42";
    private static final byte[] CONTENU = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

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
    private DocumentStorage documentStorage;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    private UUID compte;
    private String jeton;

    @BeforeEach
    void prepare_un_compte_connecte() {
        recordingNotificationSender.clear();
        compte = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);
        jeton = KnowledgeFixture.jeton(accessTokenIssuer, compte);
    }

    @AfterEach
    void efface_les_originaux() {
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    private static MockMultipartFile fichier(String nom, byte[] contenu) {
        return new MockMultipartFile("file", nom, "application/octet-stream", contenu);
    }

    private void depose(String nom, byte[] contenu) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(fichier(nom, contenu))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isCreated());
    }

    @Test
    void enregistre_un_document_depose_en_attente_de_traitement() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(fichier("rapport.pdf", CONTENU))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isCreated());

        assertThat(documentRepository.findAllByOwnerId(compte)).singleElement().satisfies(document -> {
            assertThat(document.getFilename()).isEqualTo("rapport.pdf");
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        });
    }

    @Test
    void conserve_le_fichier_d_origine() throws Exception {
        depose("rapport.pdf", CONTENU);

        UUID document = documentRepository.findAllByOwnerId(compte).getFirst().getId();

        assertThat(documentStorage.read(document))
                .hasValueSatisfying(conserve -> assertThat(conserve).isEqualTo(CONTENU));
    }

    @Test
    void refuse_un_contenu_deja_present_en_designant_le_document_existant() throws Exception {
        depose("rapport.pdf", CONTENU);
        UUID existant = documentRepository.findAllByOwnerId(compte).getFirst().getId();

        mockMvc.perform(multipart("/api/documents")
                        .file(fichier("copie-du-rapport.pdf", CONTENU))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingDocumentId").value(existant.toString()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(documentRepository.findAllByOwnerId(compte)).hasSize(1);
    }

    @Test
    void laisse_un_autre_compte_deposer_le_meme_contenu() throws Exception {
        depose("rapport.pdf", CONTENU);
        UUID bob = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);

        mockMvc.perform(multipart("/api/documents")
                        .file(fichier("rapport.pdf", CONTENU))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + KnowledgeFixture.jeton(accessTokenIssuer, bob)))
                .andExpect(status().isCreated());

        assertThat(documentRepository.findAllByOwnerId(bob)).hasSize(1);
    }

    @Test
    void refuse_un_format_non_pris_en_charge_en_enoncant_les_formats_acceptes() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(fichier("programme.exe", CONTENU))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(".pdf")));

        assertThat(documentRepository.findAllByOwnerId(compte)).isEmpty();
    }

    @Test
    void refuse_un_fichier_vide() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(fichier("rapport.pdf", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.file").isNotEmpty());
    }

    @Test
    void refuse_un_depot_sans_jeton() throws Exception {
        mockMvc.perform(multipart("/api/documents").file(fichier("rapport.pdf", CONTENU)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rattache_le_document_au_porteur_du_jeton_et_a_personne_d_autre() throws Exception {
        UUID bob = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "bob2@exemple.fr", MOT_DE_PASSE);

        depose("rapport.pdf", CONTENU);

        assertThat(documentRepository.findAllByOwnerId(compte)).hasSize(1);
        assertThat(documentRepository.findAllByOwnerId(bob)).isEmpty();
    }

    @Test
    void accepte_les_quatre_formats_annonces() throws Exception {
        depose("rapport.pdf", "un".getBytes(StandardCharsets.UTF_8));
        depose("notes.md", "deux".getBytes(StandardCharsets.UTF_8));
        depose("brouillon.txt", "trois".getBytes(StandardCharsets.UTF_8));
        depose("contrat.docx", "quatre".getBytes(StandardCharsets.UTF_8));

        assertThat(documentRepository.findAllByOwnerId(compte))
                .extracting(Document::getFilename)
                .containsExactlyInAnyOrder("rapport.pdf", "notes.md", "brouillon.txt", "contrat.docx");
    }
}
