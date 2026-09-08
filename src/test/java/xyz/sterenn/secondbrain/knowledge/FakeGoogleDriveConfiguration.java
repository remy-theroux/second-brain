package xyz.sterenn.secondbrain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
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

        private final Map<String, List<DriveFolder>> foldersByParent = new ConcurrentHashMap<>();

        private volatile boolean unavailable = false;

        private volatile boolean revoked = false;

        @Override
        public DriveAccessToken forConnection(DriveConnection connection) {
            if (revoked) {
                throw new DriveAuthorizationRevokedException();
            }
            if (unavailable) {
                throw new GoogleDriveUnavailableException();
            }
            return new DriveAccessToken(ACCESS_TOKEN, Instant.now().plus(Duration.ofHours(1)));
        }

        @Override
        public List<DriveFolder> children(DriveAccessToken accessToken, String parentId) {
            if (unavailable) {
                throw new GoogleDriveUnavailableException();
            }
            return foldersByParent.getOrDefault(parentId, List.of());
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

        public void clear() {
            foldersByParent.clear();
            unavailable = false;
            revoked = false;
        }
    }
}
