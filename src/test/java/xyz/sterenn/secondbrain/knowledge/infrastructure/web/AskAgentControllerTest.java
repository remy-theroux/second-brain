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

    private static final Duration DELAI = Duration.ofSeconds(5);

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

    private final List<String> comptesCrees = new java.util.ArrayList<>();

    private RestTestClient client;
    private UUID alice;
    private UUID document;
    private String jetonAlice;

    @BeforeEach
    void prepare_un_compte_avec_un_extrait() {
        scriptedLlmPort.clear();
        recordingEmbeddingPort.clear();
        recordingEmbeddingPort.repondra(KnowledgeFixture.uneQuestion());
        // SimpleClientHttpRequestFactory et pas la fabrique par défaut : un flux SSE est
        // chunké par nature, et le client HTTP du JDK abandonne dessus (voir CLAUDE.md).
        client = RestTestClient.bindToServer(new SimpleClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();

        alice = unCompte("alice@exemple.fr");
        jetonAlice = KnowledgeFixture.jeton(accessTokenIssuer, alice);
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
                KnowledgeFixture.uneQuestion(),
                Instant.now())));
    }

    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        // Même motif que KnowledgeEventListenerTest : les clés étrangères en cascade emportent
        // documents, extraits et traces avec le compte. Ce test n'est pas @Transactional — la
        // conversation tourne sur un autre thread et ne verrait pas la transaction du test.
        comptesCrees.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        comptesCrees.clear();
        scriptedLlmPort.clear();
        recordingEmbeddingPort.clear();
    }

    private UUID unCompte(String email) {
        comptesCrees.add(email);
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private String conversation(String question) {
        return client.post()
                .uri("/api/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice)
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
    void rend_la_reponse_au_fil_de_l_eau_puis_ses_sources_puis_la_fin() {
        scriptedLlmPort
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, DocumentAgent.PARAMETRE_QUESTION, "délai")
                .texte("Quatorze jours ", "[1].");

        String flux = conversation("Quel est le délai de rétractation ?");

        assertThat(flux).contains("event:token").containsOnlyOnce("Quatorze jours [1].");
        assertThat(flux).contains("event:sources").contains("rapport.pdf");
        assertThat(flux).contains("event:done");
        assertThat(flux.indexOf("event:sources")).isLessThan(flux.indexOf("event:done"));
    }

    @Test
    void n_envoie_jamais_une_reponse_non_sourcee() {
        scriptedLlmPort
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, DocumentAgent.PARAMETRE_QUESTION, "capitale")
                .texte("Canberra est la capitale de l'Australie.");

        String flux = conversation("Quelle est la capitale de l'Australie ?");

        assertThat(flux).doesNotContain("Canberra");
        assertThat(flux).contains("Je ne trouve pas cette information dans vos documents.");
    }

    @Test
    void ecrit_la_trace_apres_la_fermeture_du_flux() {
        scriptedLlmPort
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, DocumentAgent.PARAMETRE_QUESTION, "délai")
                .texte("Quatorze jours [1].");

        conversation("Quel est le délai de rétractation ?");

        // emitter.complete() ne bloque pas : la trace peut s'écrire après le retour de l'appel HTTP.
        await().atMost(DELAI).untilAsserted(() -> assertThat(agentRunRepository.findByOwnerId(alice))
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.getVerdict()).isEqualTo(AnswerVerdict.SOURCEE);
                    assertThat(trace.getSearches()).containsExactly("délai");
                    assertThat(trace.getSources()).hasSize(1);
                }));
    }

    @Test
    void annonce_une_erreur_quand_la_generation_est_indisponible() {
        scriptedLlmPort.tombeEnPanne();

        String flux = conversation("Quel est le délai de rétractation ?");

        assertThat(flux).contains("event:error").doesNotContain("event:done");
    }

    @Test
    void refuse_une_question_vide_sans_ouvrir_de_flux() {
        client.post()
                .uri("/api/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAlice)
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
    void refuse_une_conversation_sans_jeton() {
        client.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"question\":\"Bonjour\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void ne_voit_pas_les_documents_d_un_autre_compte() {
        jetonAlice = KnowledgeFixture.jeton(accessTokenIssuer, unCompte("bob@exemple.fr"));
        scriptedLlmPort
                .appelleOutil(DocumentAgent.OUTIL_RECHERCHE, DocumentAgent.PARAMETRE_QUESTION, "délai")
                .texte("Quatorze jours [1].");

        String flux = conversation("Quel est le délai de rétractation ?");

        assertThat(flux).contains("Je ne trouve pas cette information dans vos documents.");
    }
}
