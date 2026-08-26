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
 * Le contrat du port : l'annonce part au commit, jamais avant, jamais après un rollback.
 *
 * <p>Pas de {@code @Transactional} sur la classe : le test observe des commits, une
 * transaction englobante les masquerait. Rien n'est écrit en base ; la seule chose à
 * nettoyer est la queue d'observation, effacée en {@code @AfterEach}.
 *
 * <p>La queue est <em>durable</em> et non exclusive, à dessein : RabbitMQ 4 refuse par
 * défaut une queue transiente non exclusive ({@code transient_nonexcl_queues}, dépréciée),
 * et une queue exclusive ne se lirait que depuis la connexion qui l'a déclarée.
 *
 * <p>La queue d'observation est liée sur {@code knowledge.#} : c'est le <em>port</em> qui
 * est vérifié, par ce qui arrive sur l'exchange, pas l'adapter par ses appels internes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AmqpDomainEventPublisherTest {

    private static final String OBSERVATION = "test.observation";
    private static final long ATTENTE_MS = Duration.ofSeconds(5).toMillis();
    private static final long SILENCE_MS = Duration.ofMillis(500).toMillis();

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private DocumentUploaded evenement;

    @BeforeEach
    void ouvre_une_queue_d_observation() {
        amqpAdmin.declareQueue(new Queue(OBSERVATION));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION, Binding.DestinationType.QUEUE, AmqpConfiguration.EVENTS_EXCHANGE, "knowledge.#", null));
        amqpAdmin.purgeQueue(OBSERVATION);
        evenement = new DocumentUploaded(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z"));
    }

    @AfterEach
    void ferme_la_queue_d_observation() {
        amqpAdmin.deleteQueue(OBSERVATION);
    }

    @Test
    void publie_immediatement_hors_transaction() {
        domainEventPublisher.publish(evenement);

        Message message = rabbitTemplate.receive(OBSERVATION, ATTENTE_MS);

        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("knowledge.document.uploaded");
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("__TypeId__", "knowledge.document.uploaded");
        assertThat(rabbitTemplate.getMessageConverter().fromMessage(message)).isEqualTo(evenement);
    }

    @Test
    void publie_apres_le_commit_d_une_transaction() {
        transactionTemplate.executeWithoutResult(statut -> {
            domainEventPublisher.publish(evenement);
            // Rien ne part tant que la transaction est ouverte.
            assertThat(rabbitTemplate.receive(OBSERVATION, SILENCE_MS)).isNull();
        });

        assertThat(rabbitTemplate.receiveAndConvert(OBSERVATION, ATTENTE_MS)).isEqualTo(evenement);
    }

    @Test
    void ne_publie_rien_quand_la_transaction_est_annulee() {
        assertThatIllegalStateException()
                .isThrownBy(() -> transactionTemplate.executeWithoutResult(statut -> {
                    domainEventPublisher.publish(evenement);
                    throw new IllegalStateException("annulation volontaire");
                }))
                .withMessage("annulation volontaire");

        assertThat(rabbitTemplate.receive(OBSERVATION, SILENCE_MS)).isNull();
    }

    @Test
    void refuse_avant_le_commit_un_evenement_hors_d_un_contexte_borne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transactionTemplate.executeWithoutResult(statut ->
                        domainEventPublisher.publish(new HorsContexte(Instant.parse("2026-08-25T10:00:00Z")))))
                .withMessageContaining(HorsContexte.class.getName());

        assertThat(rabbitTemplate.receive(OBSERVATION, SILENCE_MS)).isNull();
    }

    record HorsContexte(Instant occurredAt) implements DomainEvent {}
}
