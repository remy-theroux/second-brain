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
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveChangeTokenExpiredException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChangePage;

class DriveChangeReadingTest {

    private static final DriveAccessToken ACCESS_TOKEN =
            new DriveAccessToken("ya29.jeton", Instant.now().plus(Duration.ofHours(1)));

    private final List<URI> requestedUris = new ArrayList<>();

    private MockRestServiceServer server;
    private GoogleDriveChangesAdapter adapter;

    @BeforeEach
    void wire_the_stub_server() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GoogleDriveChangesAdapter(builder);
    }

    @Test
    void asks_google_for_a_starting_point() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.START_PAGE_TOKEN_ENDPOINT)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andRespond(record("{\"startPageToken\":\"1789\"}"));

        assertThat(adapter.startPageToken(ACCESS_TOKEN)).isEqualTo("1789");
        server.verify();
    }

    @Test
    void reads_the_changes_since_the_kept_token() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andRespond(record(page(changed("f1", file("f1", "rapport.pdf", "application/pdf", "1024")), null)));

        DriveChangePage changes = adapter.changesSince(ACCESS_TOKEN, "1789");

        assertThat(changes.changes()).singleElement().satisfies(change -> {
            assertThat(change.fileId()).isEqualTo("f1");
            assertThat(change.isGone()).isFalse();
            assertThat(change.parents()).containsExactly("a1");
            assertThat(change.readableFile()).hasValueSatisfying(readable -> {
                assertThat(readable.name()).isEqualTo("rapport.pdf");
                assertThat(readable.format()).isEqualTo(DocumentFormat.PDF);
                assertThat(readable.sizeBytes()).isEqualTo(1024L);
                assertThat(readable.modifiedTime()).isEqualTo(Instant.parse("2026-09-08T10:15:30Z"));
                assertThat(readable.webViewLink()).isEqualTo("https://drive.google.com/file/d/f1/view");
            });
        });
        assertThat(query(0)).contains("pageToken=1789");
        assertThat(query(0))
                .contains("fields=changes(fileId,removed,"
                        + "file(id,name,mimeType,size,parents,trashed,modifiedTime,webViewLink)),"
                        + "nextPageToken,newStartPageToken");
        server.verify();
    }

    /** A removed change carries no file at all, hence no parents: nothing must dereference one. */
    @Test
    void reads_a_file_that_left_the_visible_drive_as_gone() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page("{\"fileId\":\"f1\",\"removed\":true}", null)));

        assertThat(adapter.changesSince(ACCESS_TOKEN, "1789").changes())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.isGone()).isTrue();
                    assertThat(change.readableFile()).isEmpty();
                    assertThat(change.parents()).isEmpty();
                });
        server.verify();
    }

    @Test
    void reads_a_file_sent_to_the_trash_as_gone() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page(changed("f1", trashed("f1", "rapport.pdf")), null)));

        assertThat(adapter.changesSince(ACCESS_TOKEN, "1789").changes())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.isGone()).isTrue();
                    assertThat(change.readableFile()).isEmpty();
                });
        server.verify();
    }

    @Test
    void reads_a_change_on_a_file_it_could_never_open_as_carrying_none() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page(changed("f1", file("f1", "photo.jpg", "image/jpeg", "4096")), null)));

        assertThat(adapter.changesSince(ACCESS_TOKEN, "1789").changes())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.isGone()).isFalse();
                    assertThat(change.readableFile()).isEmpty();
                });
        server.verify();
    }

    @Test
    void pages_through_more_changes_than_one_response_holds() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page(hundredChanges(), "page-2", null)));
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(
                        record(page(changed("f101", file("f101", "dernier.pdf", "application/pdf", "1024")), null)));

        DriveChangePage changes = adapter.changesSince(ACCESS_TOKEN, "1789");

        assertThat(changes.changes())
                .hasSize(101)
                .last()
                .extracting(DriveChange::fileId)
                .isEqualTo("f101");
        assertThat(query(0)).contains("pageToken=1789");
        assertThat(query(1)).contains("pageToken=page-2");
        server.verify();
    }

    @Test
    void hands_back_the_new_starting_token_of_the_last_page() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page("", "page-2", null)));
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page("", null, "1900")));

        assertThat(adapter.changesSince(ACCESS_TOKEN, "1789").newStartPageToken())
                .isEqualTo("1900");
        server.verify();
    }

    /**
     * A page token Google no longer knows demands a full scan, never a fresh start token: the
     * latter would let the base drift in silence, which is the very lie this flow removes.
     */
    @Test
    void demands_a_full_scan_when_google_refuses_the_page_token() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(withStatus(HttpStatus.GONE));

        assertThatExceptionOfType(DriveChangeTokenExpiredException.class)
                .isThrownBy(() -> adapter.changesSince(ACCESS_TOKEN, "perime"));
        // One expectation and no more: the adapter asked for no replacement token of its own.
        server.verify();
    }

    @Test
    void bounds_the_number_of_pages_it_follows() {
        IntStream.rangeClosed(1, GoogleDriveChangesAdapter.MAX_PAGES)
                .forEach(page -> server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                        .andRespond(record(page("", "page-" + page, null))));

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.changesSince(ACCESS_TOKEN, "1789"));
        server.verify();
    }

    @Test
    void tells_a_refused_access_token_apart_from_an_outage_when_reading_changes() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(DriveAccessTokenRejectedException.class)
                .isThrownBy(() -> adapter.changesSince(ACCESS_TOKEN, "1789"));
        server.verify();
    }

    @Test
    void reports_google_as_unavailable_when_reading_changes_fails() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(withServerError());

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.changesSince(ACCESS_TOKEN, "1789"));
        server.verify();
    }

    @Test
    void reports_google_as_unavailable_when_it_hands_back_no_starting_point() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.START_PAGE_TOKEN_ENDPOINT)))
                .andRespond(record("{}"));

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.startPageToken(ACCESS_TOKEN));
        server.verify();
    }

    @Test
    void drops_a_change_google_hands_back_without_a_file_identifier() {
        server.expect(requestTo(startsWith(GoogleDriveChangesAdapter.CHANGES_ENDPOINT)))
                .andRespond(record(page(
                        "{\"removed\":true}," + changed("f2", file("f2", "rapport.pdf", "application/pdf", "1024")),
                        null)));

        assertThat(adapter.changesSince(ACCESS_TOKEN, "1789").changes())
                .extracting(DriveChange::fileId)
                .containsExactly("f2");
        server.verify();
    }

    private static String hundredChanges() {
        return IntStream.rangeClosed(1, 100)
                .mapToObj(index ->
                        changed("f" + index, file("f" + index, "rapport-" + index + ".pdf", "application/pdf", "1024")))
                .collect(Collectors.joining(","));
    }

    private static String changed(String fileId, String fileJson) {
        return "{\"fileId\":\"" + fileId + "\",\"removed\":false,\"file\":" + fileJson + "}";
    }

    private static String file(String id, String name, String mimeType, String size) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"mimeType\":\"" + mimeType + "\",\"size\":\"" + size
                + "\",\"trashed\":false,\"parents\":[\"a1\"],\"webViewLink\":\"https://drive.google.com/file/d/" + id
                + "/view\",\"modifiedTime\":\"2026-09-08T10:15:30.000Z\"}";
    }

    private static String trashed(String id, String name) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"mimeType\":\"application/pdf\",\"size\":\"1024\",\"trashed\":true,\"parents\":[\"a1\"]}";
    }

    private static String page(String changesJson, String nextPageToken) {
        return page(changesJson, nextPageToken, "1900");
    }

    private static String page(String changesJson, String nextPageToken, String newStartPageToken) {
        String next = nextPageToken == null ? "" : ",\"nextPageToken\":\"" + nextPageToken + "\"";
        String start = newStartPageToken == null ? "" : ",\"newStartPageToken\":\"" + newStartPageToken + "\"";
        return "{\"changes\":[" + changesJson + "]" + next + start + "}";
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
