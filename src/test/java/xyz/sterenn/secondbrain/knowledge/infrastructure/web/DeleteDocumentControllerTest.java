package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Supprimer, c'est faire disparaître les deux : la ligne et son original. Un test qui ne
 * regarderait que la liste laisserait passer un stockage objet qui se remplit indéfiniment,
 * et que plus aucune ligne ne permettrait de nettoyer.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeleteDocumentControllerTest {

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
    private DocumentStorage documentStorage;

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

    private UUID depose(String jeton, UUID proprietaire, String nom) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file", nom, "application/octet-stream", nom.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(proprietaire).getFirst().getId();
    }

    @Test
    void retire_le_document_de_la_liste_et_efface_son_fichier_d_origine() throws Exception {
        UUID document = depose(jetonAlice, alice, "rapport.pdf");

        mockMvc.perform(delete("/api/documents/{id}", document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNoContent());

        assertThat(documentRepository.findAllByOwnerId(alice)).isEmpty();
        assertThat(documentStorage.read(document)).isEmpty();
    }

    @Test
    void permet_de_redeposer_un_contenu_apres_l_avoir_supprime() throws Exception {
        UUID document = depose(jetonAlice, alice, "rapport.pdf");
        mockMvc.perform(delete("/api/documents/{id}", document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNoContent());

        depose(jetonAlice, alice, "rapport.pdf");

        assertThat(documentRepository.findAllByOwnerId(alice)).hasSize(1);
    }

    @Test
    void refuse_de_supprimer_le_document_d_un_autre_compte_comme_s_il_n_existait_pas() throws Exception {
        UUID bob = AccountFixture.registerVerified(
                commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);
        UUID chezBob = depose(KnowledgeFixture.jeton(accessTokenIssuer, bob), bob, "chez-bob.pdf");

        mockMvc.perform(delete("/api/documents/{id}", chezBob)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNotFound());

        assertThat(documentRepository.findAllByOwnerId(bob)).hasSize(1);
    }

    @Test
    void refuse_de_supprimer_un_document_inexistant() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse_une_suppression_sans_jeton() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
