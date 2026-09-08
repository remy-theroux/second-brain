package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;

class GoogleDriveFilesAdapter implements GoogleDriveFiles {

    static final String FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files";

    static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    static final String FIELDS = "files(id,name,mimeType,size,webViewLink,modifiedTime),nextPageToken";

    /** A page token that never runs out would hang a request for good: it is bounded rather than trusted. */
    static final int MAX_PAGES = 100;

    /** Drive dropped multi-parenting in 2020, so a cycle cannot exist: the bound is defiance, not a fix. */
    static final int MAX_DEPTH = 50;

    /** The whole alphabet of a Drive identifier: anything else designates no folder of any Drive. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveFilesAdapter.class);

    private final RestClient restClient;

    GoogleDriveFilesAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<DriveFile> filesUnder(DriveAccessToken accessToken, String folderId) {
        if (!isDriveIdentifier(folderId)) {
            return List.of();
        }
        List<DriveFile> files = new ArrayList<>();
        collect(accessToken, folderId, files, 0);
        return files;
    }

    @Override
    public byte[] download(DriveAccessToken accessToken, String fileId) {
        try {
            byte[] content = restClient
                    .get()
                    .uri(mediaUri(fileId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                LOG.error("Google answered an empty body to a file download");
                throw new GoogleDriveUnavailableException();
            }
            return content;
        } catch (RestClientResponseException refusal) {
            LOG.error("Google refused a file download: {}", describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a file download: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    private void collect(DriveAccessToken accessToken, String folderId, List<DriveFile> files, int depth) {
        if (depth >= MAX_DEPTH) {
            LOG.error("The folders of a Drive kept nesting past {} levels", MAX_DEPTH);
            throw new GoogleDriveUnavailableException();
        }
        List<String> subfolders = new ArrayList<>();
        String pageToken = null;
        for (int page = 0; page < MAX_PAGES; page++) {
            GoogleFileListResponse answer = listOnePage(accessToken, folderId, pageToken);
            if (answer == null) {
                LOG.error("Google answered an empty body to a folder content listing");
                throw new GoogleDriveUnavailableException();
            }
            if (answer.files() != null) {
                answer.files().forEach(entry -> sort(entry, files, subfolders));
            }
            pageToken = answer.nextPageToken();
            if (pageToken == null || pageToken.isBlank()) {
                subfolders.forEach(subfolder -> collect(accessToken, subfolder, files, depth + 1));
                return;
            }
        }
        LOG.error("Google kept handing back a page token after {} pages", MAX_PAGES);
        throw new GoogleDriveUnavailableException();
    }

    private static void sort(GoogleFileResponse entry, List<DriveFile> files, List<String> subfolders) {
        if (FOLDER_MIME_TYPE.equals(entry.mimeType())) {
            if (isDriveIdentifier(entry.id())) {
                subfolders.add(entry.id());
            }
            return;
        }
        toFile(entry).ifPresent(files::add);
    }

    /** Every field of the response is nullable: one unreadable entry drops out rather than failing the walk. */
    private static Optional<DriveFile> toFile(GoogleFileResponse entry) {
        if (isBlank(entry.id()) || isBlank(entry.name())) {
            LOG.warn("Google handed back a file without an identifier or a name: dropped");
            return Optional.empty();
        }
        Optional<DocumentFormat> format = DocumentFormat.forFilename(entry.name());
        if (format.isEmpty()) {
            return Optional.empty();
        }
        return sizeOf(entry)
                .map(size -> new DriveFile(
                        entry.id(), entry.name(), format.get(), size, entry.webViewLink(), modifiedTimeOf(entry)));
    }

    /** Drive hands the size back as a string, and hands none at all back for a native Google Doc. */
    private static Optional<Long> sizeOf(GoogleFileResponse entry) {
        if (isBlank(entry.size())) {
            LOG.warn("Google told no size for the file {}: dropped", entry.id());
            return Optional.empty();
        }
        try {
            long size = Long.parseLong(entry.size().trim());
            if (size <= 0) {
                LOG.warn("Google told an empty size for the file {}: dropped", entry.id());
                return Optional.empty();
            }
            return Optional.of(size);
        } catch (NumberFormatException unreadable) {
            LOG.warn("Google told an unreadable size for the file {}: dropped", entry.id());
            return Optional.empty();
        }
    }

    private static Instant modifiedTimeOf(GoogleFileResponse entry) {
        if (isBlank(entry.modifiedTime())) {
            return null;
        }
        try {
            return Instant.parse(entry.modifiedTime());
        } catch (DateTimeParseException unreadable) {
            LOG.warn("Google told an unreadable modification time for the file {}", entry.id());
            return null;
        }
    }

    private GoogleFileListResponse listOnePage(DriveAccessToken accessToken, String folderId, String pageToken) {
        try {
            return restClient
                    .get()
                    .uri(pageUri(folderId, pageToken))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(GoogleFileListResponse.class);
        } catch (RestClientResponseException refusal) {
            LOG.error("Google refused a folder content listing: {}", describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a folder content listing: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    /** No mime type filter: the folders come back through this very listing, and nothing else recurses. */
    static URI pageUri(String folderId, String pageToken) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(FILES_ENDPOINT)
                .queryParam("q", "'" + escape(folderId) + "' in parents and trashed=false")
                .queryParam("fields", FIELDS);
        if (pageToken != null) {
            uri.queryParam("pageToken", pageToken);
        }
        return uri.build().encode().toUri();
    }

    private static URI mediaUri(String fileId) {
        return UriComponentsBuilder.fromUriString(FILES_ENDPOINT)
                .pathSegment(fileId)
                .queryParam("alt", "media")
                .build()
                .encode()
                .toUri();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isDriveIdentifier(String folderId) {
        return folderId != null && IDENTIFIER.matcher(folderId).matches();
    }

    /** The parent comes from the request, and the Drive query language reads a quote as a delimiter. */
    private static String escape(String folderId) {
        return folderId.replace("\\", "\\\\").replace("'", "\\'");
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
