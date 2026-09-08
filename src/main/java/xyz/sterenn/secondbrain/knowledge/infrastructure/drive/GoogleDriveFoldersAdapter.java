package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

class GoogleDriveFoldersAdapter implements GoogleDriveFolders {

    static final String FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files";

    static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    /** A page token that never runs out would hang a request for good: it is bounded rather than trusted. */
    static final int MAX_PAGES = 100;

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveFoldersAdapter.class);

    private final RestClient restClient;

    GoogleDriveFoldersAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<DriveFolder> children(DriveAccessToken accessToken, String parentId) {
        List<DriveFolder> folders = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            GoogleFileListResponse answer = listOnePage(accessToken, parentId, pageToken);
            if (answer == null) {
                LOG.error("Google answered an empty body to a folder listing");
                throw new GoogleDriveUnavailableException();
            }
            if (answer.files() != null) {
                answer.files().stream()
                        .map(file -> new DriveFolder(file.id(), file.name()))
                        .forEach(folders::add);
            }
            pageToken = answer.nextPageToken();
            if (pageToken == null || pageToken.isBlank()) {
                return folders;
            }
        }
        LOG.error("Google kept handing back a page token after {} pages", MAX_PAGES);
        throw new GoogleDriveUnavailableException();
    }

    private GoogleFileListResponse listOnePage(DriveAccessToken accessToken, String parentId, String pageToken) {
        try {
            return restClient
                    .get()
                    .uri(pageUri(parentId, pageToken))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(GoogleFileListResponse.class);
        } catch (RestClientException failure) {
            LOG.error("Google refused a folder listing: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    private static URI pageUri(String parentId, String pageToken) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(FILES_ENDPOINT)
                .queryParam(
                        "q",
                        "'" + escape(parentId) + "' in parents and mimeType='" + FOLDER_MIME_TYPE
                                + "' and trashed=false")
                .queryParam("fields", "files(id,name),nextPageToken");
        if (pageToken != null) {
            uri.queryParam("pageToken", pageToken);
        }
        return uri.build().encode().toUri();
    }

    /** The parent comes from the request, and the Drive query language reads a quote as a delimiter. */
    private static String escape(String parentId) {
        return parentId.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** Status and type, never {@code getMessage()}, which would echo the response body into the log. */
    private static String describe(RestClientException failure) {
        if (failure instanceof RestClientResponseException refusal) {
            return failure.getClass().getSimpleName() + " HTTP "
                    + refusal.getStatusCode().value();
        }
        return failure.getClass().getSimpleName();
    }
}
