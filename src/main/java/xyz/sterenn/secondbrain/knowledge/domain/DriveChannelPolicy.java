package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import java.time.Instant;

public final class DriveChannelPolicy {

    /**
     * What the subscription asks Google for, and it is its maximum. Asked for nothing, Google
     * grants an hour: the seven days are demanded, never given.
     */
    public static final Duration REQUESTED_LIFETIME = Duration.ofDays(7);

    /** How long before its deadline a channel is renewed, when its own lifetime leaves room for it. */
    public static final Duration RENEWAL_MARGIN = Duration.ofHours(12);

    /** The share of its own lifetime the margin may not exceed, so a short channel is not born due. */
    private static final int MARGIN_DIVISOR = 4;

    private DriveChannelPolicy() {}

    public static boolean needsRenewal(Instant openedAt, Instant expiresAt, Instant now) {
        return !now.isBefore(expiresAt.minus(marginOf(Duration.between(openedAt, expiresAt))));
    }

    /**
     * Google may hand back an hour where seven days were asked for. A fixed twelve-hour margin
     * would then make every channel due the moment it opens, and each round would pay a watch and
     * a stop for nothing — while the renewal would still fall after the deadline it watches.
     */
    private static Duration marginOf(Duration lifetime) {
        if (lifetime.isNegative()) {
            return Duration.ZERO;
        }
        Duration share = lifetime.dividedBy(MARGIN_DIVISOR);
        return share.compareTo(RENEWAL_MARGIN) < 0 ? share : RENEWAL_MARGIN;
    }
}
