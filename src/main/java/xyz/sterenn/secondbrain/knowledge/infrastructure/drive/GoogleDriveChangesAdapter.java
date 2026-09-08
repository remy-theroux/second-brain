package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveChangeTokenExpiredException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChangePage;

class GoogleDriveChangesAdapter implements GoogleDriveChanges {

    static final String CHANGES_ENDPOINT = "https://www.googleapis.com/drive/v3/changes";

    static final String START_PAGE_TOKEN_ENDPOINT = CHANGES_ENDPOINT + "/startPageToken";

    static final String FIELDS = "changes(fileId,removed,"
            + "file(id,name,mimeType,size,parents,trashed,modifiedTime,webViewLink)),"
            + "nextPageToken,newStartPageToken";

    /** A page token that never runs out would hang a run for good: it is bounded rather than trusted. */
    static final int MAX_PAGES = 100;

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveChangesAdapter.class);

    private final RestClient restClient;

    GoogleDriveChangesAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String startPageToken(DriveAccessToken accessToken) {
        GoogleStartPageTokenResponse answer = call(accessToken, startUri(), GoogleStartPageTokenResponse.class);
        if (answer == null || GoogleFiles.isBlank(answer.startPageToken())) {
            LOG.error("Google answered no starting point to a change feed");
            throw new GoogleDriveUnavailableException();
        }
        return answer.startPageToken();
    }

    @Override
    public DriveChangePage changesSince(DriveAccessToken accessToken, String pageToken) {
        List<DriveChange> changes = new ArrayList<>();
        String nextPageToken = pageToken;
        for (int page = 0; page < MAX_PAGES; page++) {
            GoogleChangeListResponse answer =
                    call(accessToken, changesUri(nextPageToken), GoogleChangeListResponse.class);
            if (answer == null) {
                LOG.error("Google answered an empty body to a change listing");
                throw new GoogleDriveUnavailableException();
            }
            if (answer.changes() != null) {
                answer.changes().forEach(entry -> toChange(entry).ifPresent(changes::add));
            }
            nextPageToken = answer.nextPageToken();
            if (GoogleFiles.isBlank(nextPageToken)) {
                return new DriveChangePage(changes, answer.newStartPageToken());
            }
        }
        LOG.error("Google kept handing back a page token after {} pages", MAX_PAGES);
        throw new GoogleDriveUnavailableException();
    }

    /**
     * The two ways of being gone, kept apart at the source: a removed change has no {@code file}
     * to read, and a trashed one is described but must not be handed on as readable. A change
     * that says neither — no {@code removed}, and no {@code file} either — is an incomplete
     * answer, not a fact: read as a removal, it would erase a document Drive said nothing about.
     */
    private static Optional<DriveChange> toChange(GoogleChangeResponse entry) {
        if (GoogleFiles.isBlank(entry.fileId())) {
            LOG.warn("Google handed back a change without a file identifier: dropped");
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(entry.removed())) {
            return Optional.of(DriveChange.removed(entry.fileId()));
        }
        if (entry.file() == null) {
            LOG.warn("Google described a change on the file {} with no file at all: left out", entry.fileId());
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(entry.file().trashed())) {
            return Optional.of(DriveChange.trashed(entry.fileId()));
        }
        return Optional.of(DriveChange.of(
                entry.fileId(),
                GoogleFiles.toFile(entry.file()).orElse(null),
                entry.file().parents()));
    }

    private <T> T call(DriveAccessToken accessToken, URI uri, Class<T> answer) {
        try {
            return restClient
                    .get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(answer);
        } catch (RestClientResponseException refusal) {
            LOG.error("Google refused a change listing: {}", describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a change listing: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    private static URI startUri() {
        return UriComponentsBuilder.fromUriString(START_PAGE_TOKEN_ENDPOINT)
                .build()
                .encode()
                .toUri();
    }

    /** {@code includeRemoved} is Google's default, written out because everything here rests on it. */
    private static URI changesUri(String pageToken) {
        return UriComponentsBuilder.fromUriString(CHANGES_ENDPOINT)
                .queryParam("pageToken", pageToken)
                .queryParam("includeRemoved", "true")
                .queryParam("fields", FIELDS)
                .build()
                .encode()
                .toUri();
    }

    /**
     * A 410 is the whole point: Google no longer knows the token, and what is owed then is a full
     * scan. Reading it as an outage would retry the same dead token for ever; replacing it with a
     * fresh starting point would let the base drift in silence.
     */
    private static RuntimeException translate(RestClientResponseException refusal) {
        HttpStatusCode status = refusal.getStatusCode();
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new DriveAccessTokenRejectedException(refusal);
        }
        if (status.isSameCodeAs(HttpStatus.GONE)) {
            return new DriveChangeTokenExpiredException(refusal);
        }
        return new GoogleDriveUnavailableException(refusal);
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
