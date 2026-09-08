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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveContentUnreachableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;

class DriveFolderScanTest {

    private static final DriveAccessToken ACCESS_TOKEN =
            new DriveAccessToken("ya29.jeton", Instant.now().plus(Duration.ofHours(1)));

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
    void walks_the_files_of_a_folder() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andRespond(record(page(file("f1", "rapport.pdf", "application/pdf", "1024"), null)));

        List<DriveFile> files = adapter.filesUnder(ACCESS_TOKEN, "a1");

        assertThat(files).singleElement().satisfies(walked -> {
            assertThat(walked.id()).isEqualTo("f1");
            assertThat(walked.name()).isEqualTo("rapport.pdf");
            assertThat(walked.format()).isEqualTo(DocumentFormat.PDF);
            assertThat(walked.sizeBytes()).isEqualTo(1024L);
            assertThat(walked.webViewLink()).isEqualTo("https://drive.google.com/file/d/f1/view");
            assertThat(walked.modifiedTime()).isEqualTo(Instant.parse("2026-09-08T10:15:30Z"));
        });
        assertThat(query(0)).contains("'a1' in parents").contains("trashed=false");
        server.verify();
    }

    /** The folders themselves must come back through the very same listing, or nothing recurses. */
    @Test
    void asks_for_the_whole_content_of_a_folder_and_not_only_its_files() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page("", null)));

        adapter.filesUnder(ACCESS_TOKEN, "a1");

        assertThat(query(0)).doesNotContain("mimeType=");
        assertThat(query(0)).contains("fields=files(id,name,mimeType,size,webViewLink,modifiedTime),nextPageToken");
        server.verify();
    }

    @Test
    void walks_the_files_of_its_subfolders() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(
                        page(file("f1", "rapport.pdf", "application/pdf", "1024") + "," + folder("b1", "2026"), null)));
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(file("f2", "notes.md", "text/markdown", "512"), null)));

        List<DriveFile> files = adapter.filesUnder(ACCESS_TOKEN, "a1");

        assertThat(files).extracting(DriveFile::id).containsExactly("f1", "f2");
        assertThat(query(1)).contains("'b1' in parents");
        server.verify();
    }

    @Test
    void pages_through_a_folder_of_more_than_a_hundred_files() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(hundredFiles(), "page-2")));
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(file("f101", "dernier.pdf", "application/pdf", "1024"), null)));

        List<DriveFile> files = adapter.filesUnder(ACCESS_TOKEN, "a1");

        assertThat(files).hasSize(101).last().extracting(DriveFile::id).isEqualTo("f101");
        assertThat(query(0)).doesNotContain("pageToken");
        assertThat(query(1)).contains("pageToken=page-2");
        server.verify();
    }

    @Test
    void ignores_a_file_whose_format_is_not_supported() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(
                        file("f1", "photo.jpg", "image/jpeg", "4096") + ","
                                + file("f2", "rapport.pdf", "application/pdf", "1024"),
                        null)));

        assertThat(adapter.filesUnder(ACCESS_TOKEN, "a1"))
                .extracting(DriveFile::id)
                .containsExactly("f2");
        server.verify();
    }

    /** Drive hands the size back as a string, and hands none at all back for a native Google Doc. */
    @Test
    void drops_a_file_whose_size_drive_does_not_tell() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(record(page(
                        "{\"id\":\"f1\",\"name\":\"memo.pdf\",\"mimeType\":\"application/pdf\"},"
                                + file("f2", "rapport.pdf", "application/pdf", "1024"),
                        null)));

        assertThat(adapter.filesUnder(ACCESS_TOKEN, "a1"))
                .extracting(DriveFile::id)
                .containsExactly("f2");
        server.verify();
    }

    @Test
    void asks_google_nothing_for_an_identifier_no_drive_could_hand_back() {
        assertThat(adapter.filesUnder(ACCESS_TOKEN, "a1' or '1'='1")).isEmpty();
        server.verify();
    }

    @Test
    void reports_google_as_unavailable_when_the_walk_fails() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withServerError());

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.filesUnder(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    @Test
    void tells_a_refused_access_token_apart_from_an_outage_when_walking() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(DriveAccessTokenRejectedException.class)
                .isThrownBy(() -> adapter.filesUnder(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    @Test
    void downloads_the_bytes_of_a_file_as_drive_holds_them() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT + "/f1")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andRespond(request -> {
                    requestedUris.add(request.getURI());
                    return withSuccess("%PDF-1.7".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF)
                            .createResponse(request);
                });

        assertThat(adapter.download(ACCESS_TOKEN, "f1")).isEqualTo("%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        assertThat(query(0)).contains("alt=media");
        server.verify();
    }

    @Test
    void tells_a_refused_access_token_apart_from_an_outage_when_downloading() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(DriveAccessTokenRejectedException.class)
                .isThrownBy(() -> adapter.download(ACCESS_TOKEN, "f1"));
        server.verify();
    }

    @Test
    void reports_google_as_unavailable_when_a_download_fails() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withServerError());

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.download(ACCESS_TOKEN, "f1"));
        server.verify();
    }

    @Test
    void tells_a_folder_google_no_longer_hands_back_apart_from_an_outage() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatExceptionOfType(DriveContentUnreachableException.class)
                .isThrownBy(() -> adapter.filesUnder(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    @Test
    void tells_a_file_that_vanished_apart_from_an_outage_when_downloading() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatExceptionOfType(DriveContentUnreachableException.class)
                .isThrownBy(() -> adapter.download(ACCESS_TOKEN, "f1"));
        server.verify();
    }

    @Test
    void tells_a_file_it_may_no_longer_read_apart_from_an_outage_when_downloading() {
        server.expect(requestTo(startsWith(GoogleDriveFilesAdapter.FILES_ENDPOINT)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatExceptionOfType(DriveContentUnreachableException.class)
                .isThrownBy(() -> adapter.download(ACCESS_TOKEN, "f1"));
        server.verify();
    }

    private static String hundredFiles() {
        return IntStream.rangeClosed(1, 100)
                .mapToObj(index -> file("f" + index, "rapport-" + index + ".pdf", "application/pdf", "1024"))
                .collect(Collectors.joining(","));
    }

    private static String file(String id, String name, String mimeType, String size) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"mimeType\":\"" + mimeType + "\",\"size\":\"" + size
                + "\",\"webViewLink\":\"https://drive.google.com/file/d/" + id
                + "/view\",\"modifiedTime\":\"2026-09-08T10:15:30.000Z\"}";
    }

    private static String folder(String id, String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"mimeType\":\"application/vnd.google-apps.folder\"}";
    }

    private static String page(String filesJson, String nextPageToken) {
        String token = nextPageToken == null ? "" : ",\"nextPageToken\":\"" + nextPageToken + "\"";
        return "{\"files\":[" + filesJson + "]" + token + "}";
    }

    /** The query travels percent-encoded: it is read back decoded rather than matched blind. */
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
