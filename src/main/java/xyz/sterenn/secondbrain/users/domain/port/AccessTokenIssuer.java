package xyz.sterenn.secondbrain.users.domain.port;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;

/** Émet un jeton désignant un compte et valable jusqu'à un instant donné, dans un format que le domaine ignore. */
public interface AccessTokenIssuer {

    AccessToken issue(UUID subject, Instant issuedAt, Instant expiresAt);
}
