package xyz.sterenn.secondbrain.shared.event.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * No {@code @Transactional} on the class: the test observes commits, which an enclosing
 * transaction would hide. The observation queue is deleted in {@code @AfterEach}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AmqpDomainEventPublisherTest {

    private static final String OBSERVATION = "test.observation";
    private static final long WAIT_MS = Duration.ofSeconds(5).toMillis();
    private static final long SILENCE_MS = Duration.ofMillis(500).toMillis();

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private DocumentUploaded event;

    @BeforeEach
    void opens_an_observation_queue() {
        amqpAdmin.declareQueue(new Queue(OBSERVATION));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION, Binding.DestinationType.QUEUE, AmqpConfiguration.EVENTS_EXCHANGE, "knowledge.#", null));
        amqpAdmin.purgeQueue(OBSERVATION);
        event = new DocumentUploaded(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z"));
    }

    @AfterEach
    void closes_the_observation_queue() {
        amqpAdmin.deleteQueue(OBSERVATION);
    }

    @Test
    void publishes_immediately_outside_a_transaction() {
        domainEventPublisher.publish(event);

        Message message = rabbitTemplate.receive(OBSERVATION, WAIT_MS);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("knowledge.document.uploaded");
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("__TypeId__", "knowledge.document.uploaded");
        assertThat(rabbitTemplate.getMessageConverter().fromMessage(message)).isEqualTo(event);
    }

    @Test
    void publishes_after_a_transaction_commits() {
        transactionTemplate.executeWithoutResult(status -> {
            domainEventPublisher.publish(event);
            assertThat(rabbitTemplate.receive(OBSERVATION, SILENCE_MS)).isNull();
        });

        assertThat(rabbitTemplate.receiveAndConvert(OBSERVATION, WAIT_MS)).isEqualTo(event);
    }

    @Test
    void publishes_nothing_when_the_transaction_is_rolled_back() {
        assertThatIllegalStateException()
                .isThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    domainEventPublisher.publish(event);
                    throw new IllegalStateException("annulation volontaire");
                }))
                .withMessage("annulation volontaire");

        assertThat(rabbitTemplate.receive(OBSERVATION, SILENCE_MS)).isNull();
    }

    @Test
    void rejects_before_the_commit_an_event_outside_a_bounded_context() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                        domainEventPublisher.publish(new OutsideContext(Instant.parse("2026-08-25T10:00:00Z")))))
                .withMessageContaining(OutsideContext.class.getName());

        assertThat(rabbitTemplate.receive(OBSERVATION, SILENCE_MS)).isNull();
    }

    record OutsideContext(Instant occurredAt) implements DomainEvent {}
}
