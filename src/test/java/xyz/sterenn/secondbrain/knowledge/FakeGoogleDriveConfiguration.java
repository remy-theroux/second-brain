package xyz.sterenn.secondbrain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

// The bean is shared by the whole context and the test transaction rollback does not
// clear it: call FakeGoogleDrive.clear() in @BeforeEach.
@TestConfiguration(proxyBeanMethods = false)
public class FakeGoogleDriveConfiguration {

    @Bean
    @Primary
    public FakeGoogleDrive fakeGoogleDrive() {
        return new FakeGoogleDrive();
    }

    /** One stub for the three ports of the context: the token exchange, then the folders and their files. */
    public static class FakeGoogleDrive implements GoogleAccessTokens, GoogleDriveFolders, GoogleDriveFiles {

        public static final String ACCESS_TOKEN = "ya29.jeton-d-acces-de-test";

        /** What the cache still hands out after the access was withdrawn: alive by the clock, dead to Drive. */
        public static final String STALE_ACCESS_TOKEN = "ya29.jeton-d-acces-perime";

        /** The same bound as the adapter's, so a programmed cycle fails here the way it would there. */
        public static final int MAX_ANCESTOR_DEPTH = 50;

        /** The modification time every programmed file carries: nothing reads it before DRIVE-5. */
        public static final Instant MODIFIED_TIME = Instant.parse("2026-09-08T10:15:30Z");

        private final Map<String, List<DriveFolder>> foldersByParent = new ConcurrentHashMap<>();

        private final Map<String, List<StoredFile>> filesByParent = new ConcurrentHashMap<>();

        private final AtomicInteger downloads = new AtomicInteger();

        private volatile int unavailableAfterDownloads = Integer.MAX_VALUE;

        private volatile boolean unavailable = false;

        private volatile boolean revoked = false;

        private volatile boolean staleAccessToken = false;

        private volatile boolean revokedOnRenewal = false;

        private volatile boolean purged = false;

        @Override
        public DriveAccessToken forConnection(DriveConnection connection) {
            if (revoked || (revokedOnRenewal && purged)) {
                throw new DriveAuthorizationRevokedException();
            }
            if (unavailable) {
                throw new GoogleDriveUnavailableException();
            }
            String value = staleAccessToken && !purged ? STALE_ACCESS_TOKEN : ACCESS_TOKEN;
            return new DriveAccessToken(value, Instant.now().plus(Duration.ofHours(1)));
        }

        @Override
        public void invalidate(DriveConnection connection) {
            purged = true;
        }

        @Override
        public List<DriveFolder> children(DriveAccessToken accessToken, String parentId) {
            refuseIfUnusable(accessToken);
            return foldersByParent.getOrDefault(parentId, List.of());
        }

        @Override
        public Optional<DriveFolder> folder(DriveAccessToken accessToken, String folderId) {
            refuseIfUnusable(accessToken);
            return foldersByParent.values().stream()
                    .flatMap(List::stream)
                    .filter(folder -> folder.id().equals(folderId))
                    .findFirst();
        }

        @Override
        public List<String> ancestors(DriveAccessToken accessToken, String folderId) {
            refuseIfUnusable(accessToken);
            List<String> ancestors = new ArrayList<>();
            String current = folderId;
            for (int depth = 0; depth < MAX_ANCESTOR_DEPTH; depth++) {
                Optional<String> parent = parentOf(current);
                if (parent.isEmpty() || DriveFolder.ROOT.equals(parent.get())) {
                    return ancestors;
                }
                ancestors.add(parent.get());
                current = parent.get();
            }
            throw new GoogleDriveUnavailableException();
        }

        /** The stub drops what it cannot read exactly where the adapter does: in the walk, silently. */
        @Override
        public List<DriveFile> filesUnder(DriveAccessToken accessToken, String folderId) {
            refuseIfUnusable(accessToken);
            List<DriveFile> files = new ArrayList<>(filesByParent.getOrDefault(folderId, List.of()).stream()
                    .map(StoredFile::toDriveFile)
                    .flatMap(Optional::stream)
                    .toList());
            foldersByParent
                    .getOrDefault(folderId, List.of())
                    .forEach(subfolder -> files.addAll(filesUnder(accessToken, subfolder.id())));
            return files;
        }

        @Override
        public byte[] download(DriveAccessToken accessToken, String fileId) {
            if (downloads.incrementAndGet() > unavailableAfterDownloads) {
                unavailable = true;
            }
            refuseIfUnusable(accessToken);
            return filesByParent.values().stream()
                    .flatMap(List::stream)
                    .filter(file -> file.id().equals(fileId))
                    .findFirst()
                    .map(StoredFile::content)
                    .orElseThrow(GoogleDriveUnavailableException::new);
        }

        private Optional<String> parentOf(String folderId) {
            return foldersByParent.entrySet().stream()
                    .filter(entry -> entry.getValue().stream()
                            .anyMatch(folder -> folder.id().equals(folderId)))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        private void refuseIfUnusable(DriveAccessToken accessToken) {
            if (unavailable) {
                throw new GoogleDriveUnavailableException();
            }
            if (STALE_ACCESS_TOKEN.equals(accessToken.value())) {
                throw new DriveAccessTokenRejectedException(new IllegalStateException("HTTP 401"));
            }
        }

        public void put(String parentId, DriveFolder... folders) {
            foldersByParent.put(parentId, List.of(folders));
        }

        public void putFile(String parentId, String fileId, String filename, byte[] content) {
            put(parentId, new StoredFile(fileId, filename, content, content.length));
        }

        /** A file Drive reports as huge: the ceiling is judged on the listing, before any download. */
        public void putOversizedFile(String parentId, String fileId, String filename, long sizeBytes) {
            put(parentId, new StoredFile(fileId, filename, new byte[] {0}, sizeBytes));
        }

        private void put(String parentId, StoredFile file) {
            filesByParent
                    .computeIfAbsent(parentId, parent -> new CopyOnWriteArrayList<>())
                    .add(file);
        }

        public static DriveFolder folder(String id, String name) {
            return new DriveFolder(id, name);
        }

        public void willBeUnavailable() {
            this.unavailable = true;
        }

        public void willReportARevokedAuthorization() {
            this.revoked = true;
        }

        /** Drive refuses the cached token, and the renewal that follows still works. */
        public void willRejectTheCachedAccessToken() {
            this.staleAccessToken = true;
        }

        /** The access was withdrawn from the Google account: the cached token dies, its renewal too. */
        public void willHaveHadItsAccessWithdrawn() {
            this.staleAccessToken = true;
            this.revokedOnRenewal = true;
        }

        /** The Drive falls over once {@code downloads} files have gone through: what is in stays in. */
        public void willBecomeUnavailableAfter(int downloads) {
            this.unavailableAfterDownloads = downloads;
        }

        public void clear() {
            foldersByParent.clear();
            filesByParent.clear();
            downloads.set(0);
            unavailableAfterDownloads = Integer.MAX_VALUE;
            unavailable = false;
            revoked = false;
            staleAccessToken = false;
            revokedOnRenewal = false;
            purged = false;
        }

        private record StoredFile(String id, String name, byte[] content, long sizeBytes) {

            Optional<DriveFile> toDriveFile() {
                return DocumentFormat.forFilename(name)
                        .map(format -> new DriveFile(
                                id,
                                name,
                                format,
                                sizeBytes,
                                "https://drive.google.com/file/d/" + id + "/view",
                                MODIFIED_TIME));
            }
        }
    }
}
