package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;

/**
 * Le rôle worker, démarré comme en production : profil {@code worker}, aucun serveur HTTP.
 *
 * <p>{@code webEnvironment = NONE} redit ce que {@code application-worker.yml} pose
 * ({@code spring.main.web-application-type=none}) : {@code @SpringBootTest} force sinon un
 * environnement servlet simulé, et le test vérifierait un contexte que le worker ne
 * construit jamais.
 *
 * <p>Le troisième scénario du socle : un événement publié est reçu par le worker. Tant
 * qu'aucune commande d'extraction n'existe, la réception se constate dans le journal ; le
 * plan d'extraction remplacera cette assertion par une lecture du statut du document.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
@ExtendWith(OutputCaptureExtension.class)
class DocumentUploadedListenerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void le_role_worker_demarre_sans_serveur_http_ni_filtre_de_securite() {
        assertThat(applicationContext).isNotInstanceOf(WebApplicationContext.class);
        assertThat(applicationContext.getBeanNamesForType(SecurityFilterChain.class))
                .isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DocumentUploadedListener.class))
                .hasSize(1);
    }

    @Test
    void recoit_l_evenement_publie(CapturedOutput sortie) {
        UUID document = UUID.randomUUID();

        // Clé de routage littérale à dessein : le test fige le contrat sur le fil. Passer
        // par DomainEventNames.of le rendrait tautologique — il vérifierait que le code
        // s'accorde avec lui-même.
        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.DocumentUploaded",
                new DocumentUploaded(document, UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z")));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(sortie)
                .contains("Événement knowledge.DocumentUploaded reçu pour le document " + document));
    }

    @Test
    void rejette_un_evenement_non_declare_sans_le_retraiter(CapturedOutput sortie) throws InterruptedException {
        UUID document = UUID.randomUUID();
        // Un corps parfaitement valide, mais annoncé sous un nom que personne n'a déclaré.
        // C'est l'en-tête de type qui gouverne (TypePrecedence.TYPE_ID) : le convertisseur
        // ne cherche pas le nom hors de ses paquets de confiance, il refuse.
        Message message = rabbitTemplate
                .getMessageConverter()
                .toMessage(
                        new DocumentUploaded(document, UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z")),
                        new MessageProperties());
        message.getMessageProperties().setHeader("__TypeId__", "knowledge.Inconnu");

        rabbitTemplate.send(AmqpConfiguration.EVENTS_EXCHANGE, "knowledge.DocumentUploaded", message);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(sortie).contains("knowledge.Inconnu"));
        // Le listener n'a pas été appelé : la conversion échoue avant lui.
        assertThat(sortie).doesNotContain("reçu pour le document " + document);

        // Et le message n'est pas remis en file (`default-requeue-rejected=false`) : sans
        // ça, un message toxique tournerait en boucle et le journal grossirait sans fin.
        int occurrences = occurrencesDe("knowledge.Inconnu", sortie);
        Thread.sleep(1000);
        assertThat(occurrencesDe("knowledge.Inconnu", sortie)).isEqualTo(occurrences);
    }

    private int occurrencesDe(String motif, CapturedOutput sortie) {
        int total = 0;
        int index = sortie.getOut().indexOf(motif);
        while (index >= 0) {
            total++;
            index = sortie.getOut().indexOf(motif, index + motif.length());
        }
        return total;
    }
}
