package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.ScriptedLlmPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.ScriptedLlmPortConfiguration.ScriptedLlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.DocumentAgent;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.AgentRunRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({
    TestcontainersConfiguration.class,
    RecordingEmbeddingPortConfiguration.class,
    ScriptedLlmPortConfiguration.class
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AskAgentControllerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private ScriptedLlmPort scriptedLlmPort;

    @Autowired
    private RecordingEmbeddingPort recordingEmbeddingPort;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    private final List<String> createdAccounts = new java.util.ArrayList<>();

    private RestTestClient client;
    private UUID alice;
    private UUID document;
    private String aliceToken;

    @BeforeEach
    void prepare_an_account_with_one_chunk() {
        scriptedLlmPort.clear();
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.willAnswer(KnowledgeFixture.aQuestion());
        // SimpleClientHttpRequestFactory and not the default factory: an SSE stream is chunked
        // by nature, and the JDK HTTP client gives up on it (see CLAUDE.md).
        client = RestTestClient.bindToServer(new SimpleClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();

        alice = anAccount("alice@exemple.fr");
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
        document = documentRepository
                .save(Document.upload(
                        alice,
                        "rapport.pdf",
                        DocumentFormat.PDF,
                        Checksum.of("contenu".getBytes(StandardCharsets.UTF_8)),
                        7L))
                .getId();
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document,
                0,
                new Chunk("Rétractation", "Le délai de rétractation est de quatorze jours."),
                KnowledgeFixture.aQuestion(),
                Instant.now())));
    }

    @AfterEach
    void erase_what_has_been_committed() {
        // Same reason as KnowledgeEventListenerTest: the cascading foreign keys take documents,
        // chunks and runs away with the account. This test is not @Transactional — the
        // conversation runs on another thread and would not see the transaction of the test.
        createdAccounts.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        createdAccounts.clear();
        scriptedLlmPort.clear();
        recordingEmbeddingPort.clear();
    }

    private UUID anAccount(String email) {
        createdAccounts.add(email);
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private String conversation(String question) {
        return client.post()
                .uri("/api/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"question\":\"" + question + "\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void streams_the_answer_then_its_sources_then_the_end() {
        scriptedLlmPort
                .callsTool(DocumentAgent.SEARCH_TOOL, DocumentAgent.QUESTION_PARAMETER, "délai")
                .text("Quatorze jours ", "[1].");

        String stream = conversation("Quel est le délai de rétractation ?");

        assertThat(stream).contains("event:token").containsOnlyOnce("Quatorze jours [1].");
        assertThat(stream).contains("event:sources").contains("rapport.pdf");
        assertThat(stream).contains("event:done");
        assertThat(stream.indexOf("event:sources")).isLessThan(stream.indexOf("event:done"));
    }

    @Test
    void never_sends_an_ungrounded_answer() {
        scriptedLlmPort
                .callsTool(DocumentAgent.SEARCH_TOOL, DocumentAgent.QUESTION_PARAMETER, "capitale")
                .text("Canberra est la capitale de l'Australie.");

        String stream = conversation("Quelle est la capitale de l'Australie ?");

        assertThat(stream).doesNotContain("Canberra");
        assertThat(stream).contains("Je ne trouve pas cette information dans vos documents.");
    }

    @Test
    void writes_the_run_after_the_stream_is_closed() {
        scriptedLlmPort
                .callsTool(DocumentAgent.SEARCH_TOOL, DocumentAgent.QUESTION_PARAMETER, "délai")
                .text("Quatorze jours [1].");

        conversation("Quel est le délai de rétractation ?");

        // emitter.complete() does not block: the run may be written after the HTTP call returns.
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(agentRunRepository.findByOwnerId(alice))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getVerdict()).isEqualTo(AnswerVerdict.GROUNDED);
                    assertThat(run.getSearches()).containsExactly("délai");
                    assertThat(run.getSources()).hasSize(1);
                }));
    }

    @Test
    void announces_an_error_when_the_generation_service_is_unavailable() {
        scriptedLlmPort.willFail();

        String stream = conversation("Quel est le délai de rétractation ?");

        assertThat(stream).contains("event:error").doesNotContain("event:done");
    }

    @Test
    void rejects_an_empty_question_without_opening_a_stream() {
        client.post()
                .uri("/api/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"question\":\"   \"}")
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors.question")
                .exists();
    }

    @Test
    void rejects_a_conversation_without_a_token() {
        client.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"question\":\"Bonjour\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void does_not_see_the_documents_of_another_account() {
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, anAccount("bob@exemple.fr"));
        scriptedLlmPort
                .callsTool(DocumentAgent.SEARCH_TOOL, DocumentAgent.QUESTION_PARAMETER, "délai")
                .text("Quatorze jours [1].");

        String stream = conversation("Quel est le délai de rétractation ?");

        assertThat(stream).contains("Je ne trouve pas cette information dans vos documents.");
    }
}
