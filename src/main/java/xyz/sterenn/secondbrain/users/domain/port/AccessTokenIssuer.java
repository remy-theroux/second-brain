package xyz.sterenn.secondbrain.users.domain.port;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;

/** Issues a token designating an account and valid until a given instant, in a format unknown to the domain. */
public interface AccessTokenIssuer {

    AccessToken issue(UUID subject, Instant issuedAt, Instant expiresAt);
}
