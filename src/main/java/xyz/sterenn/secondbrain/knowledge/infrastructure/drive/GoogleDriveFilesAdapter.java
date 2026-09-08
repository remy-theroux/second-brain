package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentTooLargeToExportException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveContentUnreachableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.GoogleWorkspaceType;

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

    /** The reasons Google gives a 403 that a later run would get past: a cap, not a refusal to read. */
    private static final Set<String> CAPPED_REASONS = Set.of(
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "dailyLimitExceeded",
            "backendError",
            "sharingRateLimitExceeded");

    /** The reason Google gives the 403 of an export it refuses on size: see {@code ImportPolicy}. */
    private static final String EXPORT_TOO_LARGE_REASON = "exportSizeLimitExceeded";

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

    @Override
    public byte[] export(DriveAccessToken accessToken, String fileId) {
        try {
            byte[] content = restClient
                    .get()
                    .uri(exportUri(fileId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .retrieve()
                    .body(byte[].class);
            if (content == null) {
                LOG.error("Google answered an empty body to a document export");
                throw new GoogleDriveUnavailableException();
            }
            return content;
        } catch (RestClientResponseException refusal) {
            LOG.error("Google refused a document export: {}", describe(refusal));
            throw translateExport(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a document export: {}", describe(failure));
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
        GoogleFiles.toFile(entry).ifPresent(files::add);
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

    /**
     * The output type of the only exportable Workspace type, written here because the port hands
     * back a file identifier and nothing else: a second exportable type would leave silently in
     * DOCX, and the fix is to carry the type down to this call.
     */
    private static URI exportUri(String fileId) {
        return UriComponentsBuilder.fromUriString(FILES_ENDPOINT)
                .pathSegment(fileId, "export")
                .queryParam("mimeType", GoogleWorkspaceType.DOCUMENT.exportMimeType())
                .build()
                .encode()
                .toUri();
    }

    private static URI mediaUri(String fileId) {
        return UriComponentsBuilder.fromUriString(FILES_ENDPOINT)
                .pathSegment(fileId)
                .queryParam("alt", "media")
                .build()
                .encode()
                .toUri();
    }

    private static boolean isDriveIdentifier(String folderId) {
        return folderId != null && IDENTIFIER.matcher(folderId).matches();
    }

    /** The parent comes from the request, and the Drive query language reads a quote as a delimiter. */
    private static String escape(String folderId) {
        return folderId.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Three refusals, three meanings, and the middle one is the whole point: a file deleted or
     * unshared between the listing and its download is the everyday accident of a walk, not an
     * outage — read as one, it would stop an import over one missing file.
     */
    private static RuntimeException translate(RestClientResponseException refusal) {
        HttpStatusCode status = refusal.getStatusCode();
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new DriveAccessTokenRejectedException(refusal);
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new DriveContentUnreachableException(refusal);
        }
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return isCapped(refusal)
                    ? new GoogleDriveUnavailableException(refusal)
                    : new DriveContentUnreachableException(refusal);
        }
        return new GoogleDriveUnavailableException(refusal);
    }

    /**
     * A 403 on an export means one more thing, and it is neither an outage nor a withdrawn
     * permission: the Doc is past the ten megabytes Google agrees to export.
     */
    private static RuntimeException translateExport(RestClientResponseException refusal) {
        if (refusal.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN) && hasReason(refusal, EXPORT_TOO_LARGE_REASON)) {
            return new DocumentTooLargeToExportException(refusal);
        }
        return translate(refusal);
    }

    private static boolean isCapped(RestClientResponseException refusal) {
        return reasonsOf(refusal).stream().anyMatch(CAPPED_REASONS::contains);
    }

    private static boolean hasReason(RestClientResponseException refusal, String reason) {
        return reasonsOf(refusal).contains(reason);
    }

    /** A body that will not parse leans towards setting one file aside rather than stopping the import. */
    private static List<String> reasonsOf(RestClientResponseException refusal) {
        try {
            GoogleApiErrorResponse body = refusal.getResponseBodyAs(GoogleApiErrorResponse.class);
            if (body == null || body.error() == null || body.error().errors() == null) {
                return List.of();
            }
            return body.error().errors().stream()
                    .map(GoogleApiErrorResponse.ErrorDetail::reason)
                    .filter(reason -> reason != null)
                    .toList();
        } catch (RestClientException unreadableBody) {
            return List.of();
        }
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
