package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Le dépassement du plafond de téléversement, sur un <strong>vrai</strong> serveur.
 *
 * <p>MockMvc ne saurait pas le vérifier : un {@code MockMultipartFile} est déjà découpé, il
 * ne traverse aucun analyseur multipart, et {@code MaxUploadSizeExceededException} n'y serait
 * jamais levée. Un test MockMvc passerait au vert quel que soit le réglage — et c'est
 * précisément le réglage qui est fragile ici : sans {@code resolve-lazily}, l'exception est
 * levée par {@code DispatcherServlet} avant qu'un contrôleur soit désigné, et
 * l'{@code @ExceptionHandler} de {@link UploadDocumentController} ne la voit jamais.
 *
 * <p><strong>Le plafond n'est pas abaissé : c'est la vraie valeur de {@code application.yml}
 * qui est éprouvée</strong>, avec un corps qui la dépasse pour de bon.
 *
 * <p>{@link SimpleClientHttpRequestFactory} n'est pas un détail : la fabrique par défaut de
 * {@code RestClient} s'appuie sur le client HTTP du JDK, qui émet ce corps en
 * {@code Transfer-Encoding: chunked} et abandonne sur une erreur d'entrée-sortie plutôt que
 * de lire la réponse que le serveur a pourtant déjà envoyée. Celle-ci tamponne le corps et
 * pose un {@code Content-Length}, comme le ferait un navigateur — et reçoit bien le 413.
 *
 * <p>Sans {@code @Transactional} : le serveur répond sur un autre fil que celui du test, une
 * transaction de test n'y aurait aucune prise. Le compte créé est donc effacé à la main.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadDocumentSizeLimitTest {

    private static final String EMAIL = "grosfichier@exemple.fr";
    private static final String MOT_DE_PASSE = "chevalpile42";

    /** Au-delà des 20 Mo de {@code spring.servlet.multipart.max-file-size}. */
    private static final int TAILLE_EXCESSIVE = 21 * 1024 * 1024;

    @LocalServerPort
    private int port;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RestTestClient client;
    private String jeton;

    @BeforeEach
    void prepare_un_compte_connecte() {
        recordingNotificationSender.clear();
        UUID compte = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, MOT_DE_PASSE);
        jeton = KnowledgeFixture.jeton(accessTokenIssuer, compte);
        client = RestTestClient.bindToServer(new SimpleClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void efface_le_compte() {
        // La cascade de la clé étrangère emporte les documents éventuels avec le compte.
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
    }

    @Test
    void refuse_un_fichier_au_dela_du_plafond_avec_un_message_affichable() {
        MultiValueMap<String, Object> corps = new LinkedMultiValueMap<>();
        corps.add("file", new ByteArrayResource(new byte[TAILLE_EXCESSIVE]) {
            @Override
            public String getFilename() {
                return "enorme.pdf";
            }
        });

        client.post()
                .uri("/api/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(enTetes -> enTetes.setBearerAuth(jeton))
                .body(corps)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectBody()
                .consumeWith(resultat -> assertThat(new String(resultat.getResponseBody(), StandardCharsets.UTF_8))
                        .contains("taille maximale"));
    }

    @Test
    void accepte_un_fichier_sous_le_plafond() {
        // Contrôle jumeau : sans lui, le test ci-dessus passerait au vert même si la route
        // refusait tout.
        MultiValueMap<String, Object> corps = new LinkedMultiValueMap<>();
        corps.add("file", new ByteArrayResource("un contenu bien modeste".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "modeste.pdf";
            }
        });

        client.post()
                .uri("/api/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(enTetes -> enTetes.setBearerAuth(jeton))
                .body(corps)
                .exchange()
                .expectStatus()
                .isCreated();
    }
}
