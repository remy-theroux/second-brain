package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;

class DriveFailuresTest {

    /** What a refusal from Google looks like once its body reached the message of the exception. */
    private static final String REFUSED_BODY =
            "400 Bad Request: [{\"message\":\"Invalid token value: jeton-de-canal-secret\"}]";

    @Test
    void names_the_types_of_a_failure_and_its_cause() {
        String described = DriveFailures.describe(new GoogleDriveUnavailableException(new IllegalStateException("x")));

        assertThat(described).isEqualTo("GoogleDriveUnavailableException <- IllegalStateException");
    }

    /** The whole point: the body of a refusal names what it refused, and that is our own secret. */
    @Test
    void never_repeats_what_the_refusal_of_google_said() {
        String described =
                DriveFailures.describe(new GoogleDriveUnavailableException(new IllegalStateException(REFUSED_BODY)));

        assertThat(described).doesNotContain("jeton-de-canal-secret").doesNotContain(REFUSED_BODY);
    }

    /** Two exceptions can name each other as their cause: the chain is bounded rather than walked. */
    @Test
    void stops_on_a_cycle_of_causes() {
        RuntimeException first = new RuntimeException("premiere");
        RuntimeException second = new RuntimeException("seconde");
        second.initCause(first);
        first.initCause(second);

        assertThat(DriveFailures.describe(first))
                .isEqualTo(
                        "RuntimeException <- RuntimeException <- RuntimeException <- RuntimeException <- RuntimeException");
    }
}
