package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

class GoogleDriveFoldersAdapter implements GoogleDriveFolders {

    static final String FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files";

    static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    /** A page token that never runs out would hang a request for good: it is bounded rather than trusted. */
    static final int MAX_PAGES = 100;

    /** Drive allows several parents per file, so a cycle is conceivable: bounded for the same reason. */
    static final int MAX_ANCESTOR_DEPTH = 50;

    /** The whole alphabet of a Drive identifier: anything else designates no folder of any Drive. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveFoldersAdapter.class);

    private final RestClient restClient;

    GoogleDriveFoldersAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<DriveFolder> children(DriveAccessToken accessToken, String parentId) {
        if (!isDriveIdentifier(parentId)) {
            return List.of();
        }
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

    @Override
    public Optional<DriveFolder> folder(DriveAccessToken accessToken, String folderId) {
        if (!isDriveIdentifier(folderId)) {
            return Optional.empty();
        }
        return get(accessToken, folderId, "id,name,mimeType,trashed")
                .filter(file -> FOLDER_MIME_TYPE.equals(file.mimeType()))
                .filter(file -> !Boolean.TRUE.equals(file.trashed()))
                .map(file -> new DriveFolder(file.id(), file.name()));
    }

    @Override
    public List<String> ancestors(DriveAccessToken accessToken, String folderId) {
        if (!isDriveIdentifier(folderId)) {
            return List.of();
        }
        List<String> ancestors = new ArrayList<>();
        String current = folderId;
        for (int depth = 0; depth < MAX_ANCESTOR_DEPTH; depth++) {
            Optional<String> parent = get(accessToken, current, "parents")
                    .map(GoogleFileResponse::parents)
                    .filter(parents -> !parents.isEmpty())
                    .map(List::getFirst);
            if (parent.isEmpty()) {
                return ancestors;
            }
            ancestors.add(parent.get());
            current = parent.get();
        }
        LOG.error("The parents of a Drive folder kept climbing past {} levels", MAX_ANCESTOR_DEPTH);
        throw new GoogleDriveUnavailableException();
    }

    private Optional<GoogleFileResponse> get(DriveAccessToken accessToken, String fileId, String fields) {
        try {
            return Optional.ofNullable(restClient
                    .get()
                    .uri(fileUri(fileId, fields))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(GoogleFileResponse.class));
        } catch (RestClientResponseException refusal) {
            if (refusal.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            LOG.error("Google refused a folder read: {}", describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a folder read: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    private GoogleFileListResponse listOnePage(DriveAccessToken accessToken, String parentId, String pageToken) {
        try {
            return restClient
                    .get()
                    .uri(pageUri(parentId, pageToken))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(GoogleFileListResponse.class);
        } catch (RestClientResponseException refusal) {
            LOG.error("Google refused a folder listing: {}", describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a folder listing: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    static URI pageUri(String parentId, String pageToken) {
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

    private static URI fileUri(String fileId, String fields) {
        return UriComponentsBuilder.fromUriString(FILES_ENDPOINT)
                .pathSegment(fileId)
                .queryParam("fields", fields)
                .build()
                .encode()
                .toUri();
    }

    private static boolean isDriveIdentifier(String folderId) {
        return folderId != null && IDENTIFIER.matcher(folderId).matches();
    }

    /** The parent comes from the request, and the Drive query language reads a quote as a delimiter. */
    private static String escape(String parentId) {
        return parentId.replace("\\", "\\\\").replace("'", "\\'");
    }

    /** A rejected token is not an outage: it is the one refusal a fresh token can settle. */
    private static GoogleDriveUnavailableException translate(RestClientResponseException refusal) {
        return refusal.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                ? new DriveAccessTokenRejectedException(refusal)
                : new GoogleDriveUnavailableException(refusal);
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
