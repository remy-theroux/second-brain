package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * La liste est la seule façon pour l'utilisateur de savoir ce que contient sa base de
 * connaissance. Ce qu'elle ne doit jamais montrer — le dépôt d'un autre compte — compte
 * donc autant que ce qu'elle montre.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListDocumentsControllerTest {

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

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

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
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    private void depose(String jeton, String nom, String contenu) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file", nom, "application/octet-stream", contenu.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isCreated());
    }

    @Test
    void rend_une_liste_vide_pour_une_base_de_connaissance_vide() throws Exception {
        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rend_le_nom_le_statut_et_la_date_de_chaque_document() throws Exception {
        depose(jetonAlice, "rapport.pdf", "contenu");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].filename").value("rapport.pdf"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void rend_les_documents_du_plus_recent_au_plus_ancien() throws Exception {
        depose(jetonAlice, "ancien.pdf", "premier");
        depose(jetonAlice, "recent.pdf", "second");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("recent.pdf"))
                .andExpect(jsonPath("$[1].filename").value("ancien.pdf"));
    }

    @Test
    void ne_montre_pas_les_documents_d_un_autre_compte() throws Exception {
        UUID bob = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);
        depose(KnowledgeFixture.jeton(accessTokenIssuer, bob), "chez-bob.pdf", "contenu");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refuse_la_liste_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void expose_le_motif_d_un_document_en_echec() throws Exception {
        depose(jetonAlice, "scan.pdf", "un contenu quelconque");
        Document document = documentRepository.findAllByOwnerId(alice).getFirst();
        document.markExtractionFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorMessage").value("Ce document ne contient pas de texte exploitable."));
    }

    @Test
    void n_expose_aucun_motif_pour_un_document_en_attente() throws Exception {
        depose(jetonAlice, "notes.md", "un contenu quelconque");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].errorMessage").doesNotExist());
    }
}
