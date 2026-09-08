package xyz.sterenn.secondbrain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentTooLargeToExportException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveChangeTokenExpiredException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveContentUnreachableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChangePage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelSubscription;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolderChain;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.GoogleWorkspaceType;

// The bean is shared by the whole context and the test transaction rollback does not
// clear it: call FakeGoogleDrive.clear() in @BeforeEach.
@TestConfiguration(proxyBeanMethods = false)
public class FakeGoogleDriveConfiguration {

    @Bean
    @Primary
    public FakeGoogleDrive fakeGoogleDrive() {
        return new FakeGoogleDrive();
    }

    /**
     * One stub for the five ports of the context: the token exchange, the folders, their files,
     * the feed of what moved, and the push subscriptions.
     */
    public static class FakeGoogleDrive
            implements GoogleAccessTokens,
                    GoogleDriveFolders,
                    GoogleDriveFiles,
                    GoogleDriveChanges,
                    GoogleDriveChannels {

        public static final String ACCESS_TOKEN = "ya29.jeton-d-acces-de-test";

        /** What the cache still hands out after the access was withdrawn: alive by the clock, dead to Drive. */
        public static final String STALE_ACCESS_TOKEN = "ya29.jeton-d-acces-perime";

        /** The same bound as the adapter's, so a programmed cycle fails here the way it would there. */
        public static final int MAX_ANCESTOR_DEPTH = 50;

        /**
         * The modification time every programmed file carries, and the pivot of a second import:
         * it alone tells a Google Doc that moved from one that did not.
         */
        public static final Instant MODIFIED_TIME = Instant.parse("2026-09-08T10:15:30Z");

        /**
         * The deadline this Google hands back, and it is shorter than the seven days of the
         * documentation on purpose: what counts is the one that comes back, never the maximum.
         */
        public static final Duration CHANNEL_LIFETIME = Duration.ofDays(3);

        private final Map<String, List<DriveFolder>> foldersByParent = new ConcurrentHashMap<>();

        private final Map<String, List<StoredFile>> filesByParent = new ConcurrentHashMap<>();

        private final Set<String> vanished = ConcurrentHashMap.newKeySet();

        private final List<DriveChange> changes = new CopyOnWriteArrayList<>();

        private final List<String> readPageTokens = new CopyOnWriteArrayList<>();

        /** Every channel call in order: what proves the new one is opened before the old is stopped. */
        private final List<String> channelCalls = new CopyOnWriteArrayList<>();

        private final Map<String, String> openChannels = new ConcurrentHashMap<>();

        private final List<String> notifiedAddresses = new CopyOnWriteArrayList<>();

        private volatile String startPageToken = "1789";

        private volatile String newStartPageToken = "4242";

        private volatile boolean changeTokenExpired = false;

        private volatile boolean ancestorsUnavailable = false;

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

        /** A folder this Drive describes nowhere is not the top of it: the climb stops short, as it does in the adapter. */
        @Override
        public DriveFolderChain ancestors(DriveAccessToken accessToken, String folderId) {
            refuseIfUnusable(accessToken);
            if (ancestorsUnavailable) {
                throw new GoogleDriveUnavailableException();
            }
            List<String> ancestors = new ArrayList<>();
            String current = folderId;
            for (int depth = 0; depth < MAX_ANCESTOR_DEPTH; depth++) {
                Optional<String> parent = parentOf(current);
                if (parent.isEmpty()) {
                    return isKnownFolder(current)
                            ? DriveFolderChain.upToTheTop(ancestors)
                            : DriveFolderChain.stoppedShort(ancestors);
                }
                if (DriveFolder.ROOT.equals(parent.get())) {
                    return DriveFolderChain.upToTheTop(ancestors);
                }
                ancestors.add(parent.get());
                current = parent.get();
            }
            throw new GoogleDriveUnavailableException();
        }

        private boolean isKnownFolder(String folderId) {
            return DriveFolder.ROOT.equals(folderId)
                    || foldersByParent.containsKey(folderId)
                    || foldersByParent.values().stream().flatMap(List::stream).anyMatch(folder -> folder.id()
                            .equals(folderId));
        }

        @Override
        public String startPageToken(DriveAccessToken accessToken) {
            refuseIfUnusable(accessToken);
            return startPageToken;
        }

        @Override
        public DriveChangePage changesSince(DriveAccessToken accessToken, String pageToken) {
            refuseIfUnusable(accessToken);
            readPageTokens.add(pageToken);
            if (changeTokenExpired) {
                throw new DriveChangeTokenExpiredException();
            }
            return new DriveChangePage(List.copyOf(changes), newStartPageToken);
        }

        @Override
        public DriveChannelSubscription watch(
                DriveAccessToken accessToken,
                String channelId,
                DriveChannelToken token,
                String address,
                String pageToken) {
            refuseIfUnusable(accessToken);
            channelCalls.add("watch:" + channelId);
            notifiedAddresses.add(address);
            String resourceId = "ressource-" + channelId;
            openChannels.put(channelId, resourceId);
            return new DriveChannelSubscription(resourceId, Instant.now().plus(CHANNEL_LIFETIME));
        }

        @Override
        public void stop(DriveAccessToken accessToken, String channelId, String resourceId) {
            refuseIfUnusable(accessToken);
            channelCalls.add("stop:" + channelId + "/" + resourceId);
            openChannels.remove(channelId);
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

        /** Drive refuses to hand back the bytes of a native Doc, which holds none: it has to be exported. */
        @Override
        public byte[] download(DriveAccessToken accessToken, String fileId) {
            return handOver(accessToken, fileId, false);
        }

        /** Google rebuilds the archive on every call: what it hands back is programmed export by export. */
        @Override
        public byte[] export(DriveAccessToken accessToken, String fileId) {
            return handOver(accessToken, fileId, true);
        }

        private byte[] handOver(DriveAccessToken accessToken, String fileId, boolean exporting) {
            if (downloads.incrementAndGet() > unavailableAfterDownloads) {
                unavailable = true;
            }
            refuseIfUnusable(accessToken);
            if (vanished.contains(fileId)) {
                throw new DriveContentUnreachableException();
            }
            StoredFile file = stored(fileId).orElseThrow(DriveContentUnreachableException::new);
            if (file.isExported() != exporting) {
                throw new DriveContentUnreachableException();
            }
            return file.next();
        }

        private Optional<StoredFile> stored(String fileId) {
            return filesByParent.values().stream()
                    .flatMap(List::stream)
                    .filter(file -> file.id().equals(fileId))
                    .findFirst();
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
            put(parentId, new StoredFile(fileId, filename, List.of(content), content.length, null));
        }

        /** A file Drive reports as huge: the ceiling is judged on the listing, before any download. */
        public void putOversizedFile(String parentId, String fileId, String filename, long sizeBytes) {
            put(parentId, new StoredFile(fileId, filename, List.of(new byte[] {0}), sizeBytes, null));
        }

        /**
         * A native Doc, whose name carries no extension and whose size Drive tells nothing about.
         * More than one export means an export that drifts: the archive Google builds embeds
         * metadata and timestamps, so two exports of an untouched Doc never carry the same bytes.
         */
        public void putGoogleDoc(String parentId, String fileId, String name, byte[]... exports) {
            put(parentId, new StoredFile(fileId, name, List.of(exports), 0, GoogleWorkspaceType.DOCUMENT));
        }

        /** A Doc past the ten megabytes Google agrees to export: no listing announces it. */
        public void putGoogleDocTooLargeToExport(String parentId, String fileId, String name) {
            put(parentId, StoredFile.refusingItsExport(fileId, name));
        }

        /** A sheet, a slide deck, a drawing: nothing downstream could read what they would export. */
        public void putWorkspaceFile(String parentId, String fileId, String name, GoogleWorkspaceType workspaceType) {
            put(parentId, new StoredFile(fileId, name, List.of(new byte[] {0}), 0, workspaceType));
        }

        /** The document was edited in Google: its modification time moves, its export follows. */
        public void willHaveBeenModifiedAt(String fileId, Instant modifiedTime) {
            stored(fileId).orElseThrow().modifiedTime = modifiedTime;
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

        /** The file was deleted or unshared between the listing and its download: a 404, never an outage. */
        public void willVanishAtDownload(String fileId) {
            vanished.add(fileId);
        }

        /** The Drive falls over once {@code downloads} files have gone through: what is in stays in. */
        public void willBecomeUnavailableAfter(int downloads) {
            this.unavailableAfterDownloads = downloads;
        }

        /** The change Drive would report for a programmed file: its name, its format, its time. */
        public DriveChange changeOn(String fileId, String... parents) {
            StoredFile file = stored(fileId).orElseThrow();
            return DriveChange.of(fileId, file.toDriveFile().orElse(null), List.of(parents));
        }

        public void willReportChanges(DriveChange... reported) {
            changes.clear();
            changes.addAll(List.of(reported));
        }

        /** Google no longer knows the kept token: what is owed is a full scan, never a fresh start. */
        public void willRefuseTheChangeToken() {
            this.changeTokenExpired = true;
        }

        /**
         * The Drive falls over exactly when the folders above a file are read, and nowhere else:
         * read as a file that left every watched folder, the hiccup would erase the base.
         */
        public void willBeUnavailableWhileResolvingParents() {
            this.ancestorsUnavailable = true;
        }

        public String newStartPageToken() {
            return newStartPageToken;
        }

        public String startPageToken() {
            return startPageToken;
        }

        /** How many files were handed over: a Doc re-exported for nothing shows up here. */
        public int handedOverFiles() {
            return downloads.get();
        }

        public List<String> readPageTokens() {
            return List.copyOf(readPageTokens);
        }

        public List<String> channelCalls() {
            return List.copyOf(channelCalls);
        }

        /** The channels Google would still notify, by identifier. */
        public Set<String> openChannels() {
            return Set.copyOf(openChannels.keySet());
        }

        public List<String> notifiedAddresses() {
            return List.copyOf(notifiedAddresses);
        }

        public void clear() {
            changes.clear();
            readPageTokens.clear();
            channelCalls.clear();
            openChannels.clear();
            notifiedAddresses.clear();
            changeTokenExpired = false;
            ancestorsUnavailable = false;
            foldersByParent.clear();
            filesByParent.clear();
            vanished.clear();
            downloads.set(0);
            unavailableAfterDownloads = Integer.MAX_VALUE;
            unavailable = false;
            revoked = false;
            staleAccessToken = false;
            revokedOnRenewal = false;
            purged = false;
        }

        private static final class StoredFile {

            private final String id;
            private final String name;
            private final List<byte[]> exports;
            private final long sizeBytes;
            private final GoogleWorkspaceType workspaceType;
            private final AtomicInteger handedOver = new AtomicInteger();
            private final boolean refusesItsExport;

            private volatile Instant modifiedTime = MODIFIED_TIME;

            private StoredFile(
                    String id, String name, List<byte[]> exports, long sizeBytes, GoogleWorkspaceType workspaceType) {
                this(id, name, exports, sizeBytes, workspaceType, false);
            }

            private StoredFile(
                    String id,
                    String name,
                    List<byte[]> exports,
                    long sizeBytes,
                    GoogleWorkspaceType workspaceType,
                    boolean refusesItsExport) {
                this.id = id;
                this.name = name;
                this.exports = exports;
                this.sizeBytes = sizeBytes;
                this.workspaceType = workspaceType;
                this.refusesItsExport = refusesItsExport;
            }

            static StoredFile refusingItsExport(String id, String name) {
                return new StoredFile(id, name, List.of(new byte[] {0}), 0, GoogleWorkspaceType.DOCUMENT, true);
            }

            String id() {
                return id;
            }

            boolean isExported() {
                return workspaceType != null;
            }

            /** The last programmed export sticks: a file handed back twice is the rule, not the exception. */
            byte[] next() {
                if (refusesItsExport) {
                    throw new DocumentTooLargeToExportException(new IllegalStateException("HTTP 403"));
                }
                return exports.get(Math.min(handedOver.getAndIncrement(), exports.size() - 1));
            }

            Optional<DriveFile> toDriveFile() {
                String link = "https://drive.google.com/file/d/" + id + "/view";
                if (workspaceType != null) {
                    return workspaceType.exportedFormat().isEmpty()
                            ? Optional.empty()
                            : Optional.of(DriveFile.exported(workspaceType, id, name, link, modifiedTime));
                }
                return DocumentFormat.forFilename(name)
                        .map(format -> DriveFile.downloaded(id, name, format, sizeBytes, link, modifiedTime));
            }
        }
    }
}
