package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

class GoogleDriveFoldersAdapterTest {

    private static final DriveAccessToken ACCESS_TOKEN =
            new DriveAccessToken("ya29.jeton", Instant.now().plus(Duration.ofHours(1)));

    private final List<URI> requestedUris = new ArrayList<>();

    private MockRestServiceServer server;
    private GoogleDriveFoldersAdapter adapter;

    @BeforeEach
    void wire_the_stub_server() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GoogleDriveFoldersAdapter(builder);
    }

    @Test
    void asks_google_for_the_folders_of_a_parent_and_for_nothing_else() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andRespond(record(page("[{\"id\":\"b1\",\"name\":\"2025\"}]", null)));

        List<DriveFolder> folders = adapter.children(ACCESS_TOKEN, "a1");

        assertThat(folders).containsExactly(new DriveFolder("b1", "2025"));
        assertThat(query(0))
                .contains("'a1' in parents")
                .contains("mimeType='" + GoogleDriveFoldersAdapter.FOLDER_MIME_TYPE + "'")
                .contains("trashed=false");
        server.verify();
    }

    @Test
    void browses_the_root_under_its_keyword() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record(page("[]", null)));

        adapter.children(ACCESS_TOKEN, DriveFolder.ROOT);

        assertThat(query(0)).contains("'root' in parents");
        server.verify();
    }

    @Test
    void follows_the_page_token_rather_than_losing_the_folders_beyond_the_first_page() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record(page("[{\"id\":\"b1\",\"name\":\"2025\"}]", "page-2")));
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record(page("[{\"id\":\"b2\",\"name\":\"2026\"}]", null)));

        List<DriveFolder> folders = adapter.children(ACCESS_TOKEN, "a1");

        assertThat(folders).containsExactly(new DriveFolder("b1", "2025"), new DriveFolder("b2", "2026"));
        assertThat(query(0)).doesNotContain("pageToken");
        assertThat(query(1)).contains("pageToken=page-2");
        server.verify();
    }

    @Test
    void reports_google_as_unavailable_when_the_listing_fails() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(withServerError());

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.children(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    @Test
    void tells_a_refused_access_token_apart_from_an_outage_when_listing() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(DriveAccessTokenRejectedException.class)
                .isThrownBy(() -> adapter.children(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    @Test
    void tells_a_refused_access_token_apart_from_an_unknown_folder() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(DriveAccessTokenRejectedException.class)
                .isThrownBy(() -> adapter.folder(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    @Test
    void reads_one_folder_by_its_identifier() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT + "/a1")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(record("{\"id\":\"a1\",\"name\":\"Notes\",\"mimeType\":\""
                        + GoogleDriveFoldersAdapter.FOLDER_MIME_TYPE + "\",\"trashed\":false}"));

        assertThat(adapter.folder(ACCESS_TOKEN, "a1")).contains(new DriveFolder("a1", "Notes"));
        assertThat(query(0)).contains("fields=id,name,mimeType,trashed");
        server.verify();
    }

    @Test
    void knows_no_folder_behind_an_identifier_google_refuses() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(withResourceNotFound());

        assertThat(adapter.folder(ACCESS_TOKEN, "inconnu")).isEmpty();
        server.verify();
    }

    @Test
    void knows_no_folder_behind_the_identifier_of_a_file() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record("{\"id\":\"f1\",\"name\":\"notes.pdf\",\"mimeType\":\"application/pdf\"}"));

        assertThat(adapter.folder(ACCESS_TOKEN, "f1")).isEmpty();
        server.verify();
    }

    @Test
    void climbs_the_parents_up_to_the_top_of_the_drive() {
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record("{\"parents\":[\"b1\"]}"));
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record("{\"parents\":[\"a1\"]}"));
        server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                .andRespond(record("{}"));

        assertThat(adapter.ancestors(ACCESS_TOKEN, "c1")).containsExactly("b1", "a1");
        assertThat(query(0)).contains("fields=parents");
        server.verify();
    }

    /** Drive allows several parents per file: a cycle must stop the climb, not the server. */
    @Test
    void refuses_to_climb_for_ever_when_the_parents_form_a_cycle() {
        for (int call = 0; call < GoogleDriveFoldersAdapter.MAX_ANCESTOR_DEPTH; call++) {
            server.expect(requestTo(startsWith(GoogleDriveFoldersAdapter.FILES_ENDPOINT)))
                    .andRespond(record("{\"parents\":[\"a1\"]}"));
        }

        assertThatExceptionOfType(GoogleDriveUnavailableException.class)
                .isThrownBy(() -> adapter.ancestors(ACCESS_TOKEN, "a1"));
        server.verify();
    }

    private static String page(String filesJson, String nextPageToken) {
        String token = nextPageToken == null ? "" : ",\"nextPageToken\":\"" + nextPageToken + "\"";
        return "{\"files\":" + filesJson + token + "}";
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
