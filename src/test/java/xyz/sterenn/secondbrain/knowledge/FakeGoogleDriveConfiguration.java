package xyz.sterenn.secondbrain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
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

    /** One stub for the two ports a browsing needs: the token exchange, then the folders. */
    public static class FakeGoogleDrive implements GoogleAccessTokens, GoogleDriveFolders {

        public static final String ACCESS_TOKEN = "ya29.jeton-d-acces-de-test";

        /** What the cache still hands out after the access was withdrawn: alive by the clock, dead to Drive. */
        public static final String STALE_ACCESS_TOKEN = "ya29.jeton-d-acces-perime";

        /** The same bound as the adapter's, so a programmed cycle fails here the way it would there. */
        public static final int MAX_ANCESTOR_DEPTH = 50;

        private final Map<String, List<DriveFolder>> foldersByParent = new ConcurrentHashMap<>();

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

        public void clear() {
            foldersByParent.clear();
            unavailable = false;
            revoked = false;
            staleAccessToken = false;
            revokedOnRenewal = false;
            purged = false;
        }
    }
}
