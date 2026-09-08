package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import java.time.Instant;

public final class DriveChannelPolicy {

    /**
     * How long before its deadline a channel is renewed. Wide on purpose: Google caps a
     * subscription at seven days but may hand back a shorter deadline without warning, and a
     * margin that outlasts the deadline itself simply renews at once, which is the right answer.
     */
    public static final Duration RENEWAL_MARGIN = Duration.ofHours(12);

    private DriveChannelPolicy() {}

    public static boolean needsRenewal(Instant expiresAt, Instant now) {
        return now.isAfter(expiresAt.minus(RENEWAL_MARGIN));
    }
}
