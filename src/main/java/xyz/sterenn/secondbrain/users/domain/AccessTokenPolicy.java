package xyz.sterenn.secondbrain.users.domain;

import java.time.Duration;
import java.time.Instant;

public final class AccessTokenPolicy {

    public static final Duration LIFETIME = Duration.ofHours(1);

    private AccessTokenPolicy() {}

    public static Instant expiresAt(Instant maintenant) {
        return maintenant.plus(LIFETIME);
    }
}
