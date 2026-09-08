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
 * MockMvc could not check this: a {@code MockMultipartFile} is already parsed, so
 * {@code MaxUploadSizeExceededException} would never be raised there. And
 * {@link SimpleClientHttpRequestFactory} is not a detail: the default factory sends the body in
 * {@code Transfer-Encoding: chunked} and gives up before reading the 413 already sent.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReplaceDocumentContentSizeLimitTest {

    private static final String EMAIL = "grosremplacement@exemple.fr";
    private static final String PASSWORD = "chevalpile42";

    /** Beyond the 20 MB of {@code spring.servlet.multipart.max-file-size}. */
    private static final int EXCESSIVE_SIZE = 21 * 1024 * 1024;

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
    private String token;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        UUID account = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, PASSWORD);
        token = KnowledgeFixture.token(accessTokenIssuer, account);
        client = RestTestClient.bindToServer(new SimpleClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void erase_the_account() {
        // Without @Transactional: the server answers on another thread, no test transaction has
        // any hold on it. The cascade takes the documents away with the account.
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
    }

    @Test
    void refuses_a_file_beyond_the_ceiling_with_a_displayable_message() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[EXCESSIVE_SIZE]) {
            @Override
            public String getFilename() {
                return "enorme.pdf";
            }
        });

        client.put()
                .uri("/api/documents/{id}", UUID.randomUUID())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> headers.setBearerAuth(token))
                .body(body)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectBody()
                .consumeWith(result -> assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8))
                        .contains("taille maximale"));
    }
}
