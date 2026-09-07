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
    private String aliceToken;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.willAnswer(KnowledgeFixture.aQuestion());
        alice = userRepository
                .save(User.register(new Email("alice@exemple.fr"), "empreinte"))
                .getId();
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    @AfterEach
    void leaves_the_embedding_port_as_it_found_it() {
        recordingEmbeddingPort.clear();
    }

    @Test
    void returns_the_content_the_document_the_position_and_the_score_of_each_chunk() throws Exception {
        Document document = anUploadedDocument("rapport-annuel.md");
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(),
                2,
                new Chunk("Introduction", "Le corps de l'extrait."),
                KnowledgeFixture.aNearbyVector(1f),
                Instant.now())));

        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(document.getId().toString()))
                .andExpect(jsonPath("$[0].filename").value("rapport-annuel.md"))
                .andExpect(jsonPath("$[0].position").value(2))
                .andExpect(jsonPath("$[0].heading").value("Introduction"))
                .andExpect(jsonPath("$[0].text").value("Le corps de l'extrait."))
                .andExpect(jsonPath("$[0].similarity").isNumber());
    }

    @Test
    void returns_an_empty_list_for_an_empty_knowledge_base() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refuses_without_a_token() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "Quelle est la réponse ?"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refuses_an_empty_question_by_naming_the_parameter() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   ").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.q").value("La question ne peut pas être vide."));
    }

    @Test
    void refuses_a_missing_question_like_an_empty_question() throws Exception {
        mockMvc.perform(get("/api/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.q").value("La question ne peut pas être vide."));
    }

    @Test
    void answers_unavailable_when_the_embedding_service_does_not_answer() throws Exception {
        recordingEmbeddingPort.willFail();

        mockMvc.perform(get("/api/search")
                        .param("q", "Quelle est la réponse ?")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private Document anUploadedDocument(String filename) {
        byte[] bytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        return documentRepository.save(
                Document.upload(alice, filename, DocumentFormat.MARKDOWN, Checksum.of(bytes), bytes.length));
    }
}
