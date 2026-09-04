package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchChunksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmbeddingPort recordingEmbeddingPort;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    private UUID alice;
    private String jetonAlice;

    @BeforeEach
    void prepare_un_compte_connecte() {
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.repondra(KnowledgeFixture.uneQuestion());
        alice = userRepository
                .save(User.register(new Email("alice@exemple.fr"), "empreinte"))
                .getId();
        jetonAlice = KnowledgeFixture.jeton(accessTokenIssuer, alice);
    }

    @AfterEach
    void rend_le_port_de_vectorisation_comme_il_l_a_trouve() {
        recordingEmbeddingPort.clear();
    }

    @Test
    void rend_le_contenu_le_document_la_position_et_le_score_de_chaque_extrait() throws Exception {
        Document document = unDocumentDepose("rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                2,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.unVecteurProche(1f),
                Instant.now())));

        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(document.getId().toString()))
                .andExpect(jsonPath("$[0].filename").value("rapport-annuel.md"))
                .andExpect(jsonPath("$[0].position").value(2))
                .andExpect(jsonPath("$[0].heading").value("Introduction"))
                .andExpect(jsonPath("$[0].text").value("Le corps de l'extrait."))
                .andExpect(jsonPath("$[0].similarity").isNumber());
    }

    @Test
    void rend_une_liste_vide_pour_une_base_de_connaissance_vide() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refuse_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "Quelle est la réponse ?"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_une_question_vide_en_nommant_le_parametre() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   ").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.q").value("La question ne peut pas être vide."));
    }

    @Test
    void refuse_une_question_absente_comme_une_question_vide() throws Exception {
        mockMvc.perform(get("/api/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.q").value("La question ne peut pas être vide."));
    }

    @Test
    void repond_indisponible_quand_la_vectorisation_ne_repond_pas() throws Exception {
        recordingEmbeddingPort.tombeEnPanne();

        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private Document unDocumentDepose(String nom) {
        byte[] octets = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(alice, nom, DocumentFormat.MARKDOWN, Checksum.of(octets), octets.length));
    }
}
