package xyz.sterenn.secondbrain.shared.event.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.event.TestEvents;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

class DomainEventNamesTest {

    @Test
    void names_by_the_context_the_object_and_the_fact() {
        assertThat(DomainEventNames.of(DocumentUploaded.class)).isEqualTo("knowledge.document.uploaded");
    }

    @Test
    void joins_the_words_of_the_object_with_a_dash() {
        assertThat(DomainEventNames.of(DocumentTextExtracted.class)).isEqualTo("knowledge.document-text.extracted");
    }

    @Test
    void rejects_a_name_without_an_object() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(TestEvents.Uploaded.class))
                .withMessageContaining("object");
    }

    @Test
    void rejects_an_event_outside_a_bounded_context() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(OutsideContext.class))
                .withMessageContaining(OutsideContext.class.getName());
    }

    @Test
    void rejects_a_lambda_or_an_anonymous_class() {
        DomainEvent lambda = () -> Instant.parse("2026-08-25T10:00:00Z");
        DomainEvent anonymous = new DomainEvent() {
            @Override
            public Instant occurredAt() {
                return Instant.parse("2026-08-25T10:00:00Z");
            }
        };

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(lambda.getClass()))
                .withMessageContaining("anonymous");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(anonymous.getClass()))
                .withMessageContaining("anonymous");
    }

    @Test
    void builds_the_table_of_known_names() {
        assertThat(DomainEventNames.mappingOf(List.of(DocumentUploaded.class)))
                .containsExactly(java.util.Map.entry("knowledge.document.uploaded", DocumentUploaded.class));
    }

    @Test
    void rejects_two_classes_with_the_same_name() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DomainEventNames.mappingOf(List.of(DocumentUploaded.class, DocumentUploaded.class)))
                .withMessageContaining("knowledge.document.uploaded");
    }

    record OutsideContext(Instant occurredAt) implements DomainEvent {}
}
