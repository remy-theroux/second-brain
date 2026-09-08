package xyz.sterenn.secondbrain.knowledge;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveGrant;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

// The bean is shared by the whole context and the test transaction rollback does not
// clear it: call FakeGoogleDriveAuthorization.clear() in @BeforeEach.
@TestConfiguration(proxyBeanMethods = false)
public class FakeGoogleDriveAuthorizationConfiguration {

    @Bean
    @Primary
    public FakeGoogleDriveAuthorization fakeGoogleDriveAuthorization() {
        return new FakeGoogleDriveAuthorization();
    }

    public static class FakeGoogleDriveAuthorization implements GoogleDriveAuthorization {

        public static final String CONSENT_PREFIX = "https://accounts.google.test/consentement?state=";

        private static final DriveGrant DEFAULT_GRANT =
                new DriveGrant("compte@gmail.com", new RefreshToken("1//jeton-de-rafraichissement"));

        private final List<String> exchangedCodes = new CopyOnWriteArrayList<>();

        private volatile DriveGrant grant = DEFAULT_GRANT;

        private volatile boolean unavailable = false;

        @Override
        public URI consentUrl(DriveAuthorizationState state) {
            return URI.create(CONSENT_PREFIX + state.value());
        }

        @Override
        public DriveGrant exchange(String authorizationCode) {
            exchangedCodes.add(authorizationCode);
            if (unavailable) {
                throw new GoogleDriveUnavailableException();
            }
            return grant;
        }

        public void willGrant(String googleEmail, String refreshToken) {
            this.grant = new DriveGrant(googleEmail, new RefreshToken(refreshToken));
        }

        public void willBeUnavailable() {
            this.unavailable = true;
        }

        public List<String> exchangedCodes() {
            return List.copyOf(exchangedCodes);
        }

        public void clear() {
            exchangedCodes.clear();
            grant = DEFAULT_GRANT;
            unavailable = false;
        }
    }
}
