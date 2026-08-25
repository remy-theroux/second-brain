package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.DocumentUploaded",
                new DocumentUploaded(document, UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z")));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(sortie)
                .contains("Événement knowledge.DocumentUploaded reçu pour le document " + document));
    }
}
