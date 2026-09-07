package xyz.sterenn.secondbrain.knowledge.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

class DocumentUploadedTest {

    private static final UUID DOCUMENT = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant INSTANT = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void is_a_dated_domain_event() {
        DomainEvent event = new DocumentUploaded(DOCUMENT, ACCOUNT, INSTANT);

        assertThat(event.occurredAt()).isEqualTo(INSTANT);
    }

    @Test
    void rejects_a_missing_document() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DocumentUploaded(null, ACCOUNT, INSTANT))
                .withMessage("L'identifiant du document est obligatoire");
    }

    @Test
    void rejects_a_missing_owner() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DocumentUploaded(DOCUMENT, null, INSTANT))
                .withMessage("Le propriétaire du document est obligatoire");
    }

    @Test
    void rejects_a_missing_instant() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DocumentUploaded(DOCUMENT, ACCOUNT, null))
                .withMessage("L'instant de l'événement est obligatoire");
    }
}
