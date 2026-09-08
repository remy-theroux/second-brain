package xyz.sterenn.secondbrain.knowledge.application.drive;

import java.util.function.Function;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;

/** Every Drive call of this context goes through here: it is what turns a rejected token into a cause. */
@Component
public class DriveAccess {

    private final GoogleAccessTokens googleAccessTokens;

    public DriveAccess(GoogleAccessTokens googleAccessTokens) {
        this.googleAccessTokens = googleAccessTokens;
    }

    /**
     * A cached token outlives the access it carries: purging it and replaying the call once is
     * what makes the refresh happen, hence what reveals a revocation. Once, never in a loop —
     * a refusal that survives a fresh token would otherwise become a storm of calls.
     */
    public <R> R call(DriveConnection connection, Function<DriveAccessToken, R> call) {
        try {
            return call.apply(googleAccessTokens.forConnection(connection));
        } catch (DriveAccessTokenRejectedException rejected) {
            googleAccessTokens.invalidate(connection);
            return call.apply(googleAccessTokens.forConnection(connection));
        }
    }
}
