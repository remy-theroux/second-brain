package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import java.time.Instant;

public final class DriveAuthorizationPolicy {

    public static final Duration VALIDITY = Duration.ofMinutes(10);

    private DriveAuthorizationPolicy() {}

    public static boolean isExpired(Instant openedAt, Instant now) {
        return now.isAfter(openedAt.plus(VALIDITY));
    }
}
