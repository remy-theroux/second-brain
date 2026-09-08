package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentTooLargeToExportException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveContentUnreachableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.GoogleWorkspaceType;

class GoogleDocExportTest {

    private static final DriveAccessToken ACCESS_TOKEN =
            new DriveAccessToken("ya29.jeton", Instant.now().plus(Duration.ofHours(1)));

    private static final byte[] DOCX = "PK".getBytes(StandardCharsets.UTF_8);

    private final List<URI> requestedUris = new ArrayList<>();

    private MockRestServiceServer server;
    private GoogleDriveFilesAdapter adapter;

    @BeforeEach
    void wire_the_stub_server() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GoogleDriveFilesAdapter(builder);
    }

    @Test
    void exports_a_google_document_as_a_docx_archive() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT + "/g1/export")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andRespond(request -> {
                    requestedUris.add(request.getURI());
                    return withSuccess(DOCX, MediaType.APPLICATION_OCTET_STREAM).createResponse(request);
                });

        assertThat(adapter.export(ACCESS_TOKEN, "g1")).isEqualTo(DOCX);
        assertThat(query(0)).contains("mimeType=" + DocumentFormat.DOCX.mediaType());
        server.verify();
    }

    /** The Doc has no size in the listing: Google's own ceiling can only be met on its refusal. */
    @Test
    void reports_a_document_whose_export_exceeds_the_ceiling_of_google() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(forbidden("global", "exportSizeLimitExceeded")));

        assertThatExceptionOfType(DocumentTooLargeToExportException.class)
                .isThrownBy(() -> adapter.export(ACCESS_TOKEN, "g1"))
                .withMessageContaining("10 Mo");
        server.verify();
    }

    @Test
    void tells_a_permission_it_no_longer_has_apart_from_a_document_too_large_to_export() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(forbidden("global", "insufficientFilePermissions")));

        assertThatExceptionOfType(DriveContentUnreachableException.class)
                .isThrownBy(() -> adapter.export(ACCESS_TOKEN, "g1"));
        server.verify();
    }

    @Test
    void reports_google_as_unavailable_when_an_export_fails() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withServerError());

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.export(ACCESS_TOKEN, "g1"));
        server.verify();
    }

    @Test
    void lists_a_native_google_document_under_its_drive_name_and_the_format_it_exports_into() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(googleDoc("g1", "Compte rendu du 3 mars"))));

        assertThat(adapter.filesUnder(ACCESS_TOKEN, "a1")).singleElement().satisfies(doc -> {
            assertThat(doc.id()).isEqualTo("g1");
            assertThat(doc.name()).isEqualTo("Compte rendu du 3 mars");
            assertThat(doc.documentName()).isEqualTo("Compte rendu du 3 mars.docx");
            assertThat(doc.format()).isEqualTo(DocumentFormat.DOCX);
            assertThat(doc.workspaceType()).isEqualTo(GoogleWorkspaceType.DOCUMENT);
            assertThat(doc.modifiedTime()).isEqualTo(Instant.parse("2026-09-08T10:15:30Z"));
        });
        server.verify();
    }

    /** Each of them would want its own typology and its own tables: see ADR-0030. */
    @Test
    void leaves_the_other_workspace_types_out_of_the_listing_in_silence() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(workspaceFile("s1", "Budget", "application/vnd.google-apps.spreadsheet") + ","
                        + workspaceFile("p1", "Soutenance", "application/vnd.google-apps.presentation") + ","
                        + workspaceFile("d1", "Schéma", "application/vnd.google-apps.drawing") + ","
                        + googleDoc("g1", "Compte rendu"))));

        assertThat(adapter.filesUnder(ACCESS_TOKEN, "a1"))
                .extracting(DriveFile::id)
                .containsExactly("g1");
        server.verify();
    }

    private static String googleDoc(String id, String name) {
        return workspaceFile(id, name, "application/vnd.google-apps.document");
    }

    /** No {@code size}: Drive hands none back for a file that holds no bytes of its own. */
    private static String workspaceFile(String id, String name, String mimeType) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"mimeType\":\"" + mimeType
                + "\",\"webViewLink\":\"https://drive.google.com/file/d/" + id
                + "/view\",\"modifiedTime\":\"2026-09-08T10:15:30.000Z\"}";
    }

    private static String page(String filesJson) {
        return "{\"files\":[" + filesJson + "]}";
    }

    private static String forbidden(String domain, String reason) {
        return "{\"error\":{\"errors\":[{\"domain\":\"" + domain + "\",\"reason\":\"" + reason
                + "\",\"message\":\"Refus.\"}],\"code\":403,\"message\":\"Refus.\"}}";
    }

    private String query(int callIndex) {
        return URLDecoder.decode(requestedUris.get(callIndex).getRawQuery(), StandardCharsets.UTF_8);
    }

    private ResponseCreator record(String body) {
        return request -> {
            requestedUris.add(request.getURI());
            return withSuccess(body, MediaType.APPLICATION_JSON).createResponse(request);
        };
    }
}
