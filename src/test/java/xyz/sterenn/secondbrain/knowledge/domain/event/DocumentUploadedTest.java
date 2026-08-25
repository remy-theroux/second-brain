package xyz.sterenn.secondbrain.knowledge.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

class DocumentUploadedTest {

    private static final UUID DOCUMENT = UUID.randomUUID();
    private static final UUID COMPTE = UUID.randomUUID();
    private static final Instant INSTANT = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void est_un_evenement_metier_date() {
        DomainEvent event = new DocumentUploaded(DOCUMENT, COMPTE, INSTANT);

        assertThat(event.occurredAt()).isEqualTo(INSTANT);
    }

    @Test
    void refuse_un_document_absent() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DocumentUploaded(null, COMPTE, INSTANT))
                .withMessage("L'identifiant du document est obligatoire");
    }

    @Test
    void refuse_un_proprietaire_absent() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DocumentUploaded(DOCUMENT, null, INSTANT))
                .withMessage("Le propriétaire du document est obligatoire");
    }

    @Test
    void refuse_un_instant_absent() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DocumentUploaded(DOCUMENT, COMPTE, null))
                .withMessage("L'instant de l'événement est obligatoire");
    }
}
