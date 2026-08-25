# Événements métier sur RabbitMQ et rôle worker — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un handler publie un événement métier qui ne part qu'au commit ; un processus
worker distinct de l'API le reçoit depuis RabbitMQ et dispatche une commande sur le bus.

**Architecture:** Port `DomainEventPublisher` dans `shared/event/` (sans Spring), adapter
AMQP dans `shared/event/amqp/` qui envoie dans `afterCommit`. Un exchange topic
`second-brain.events`, clé de routage `knowledge.DocumentUploaded` dérivée de la classe.
Un listener par événement dans `<contexte>/infrastructure/messaging/`, actif sous le profil
`worker` seulement ; ce profil coupe Tomcat. Même image, second conteneur.

**Tech Stack:** Spring Boot 4.0.7 · Spring AMQP 4.0.4 (`spring-boot-starter-amqp`) ·
Jackson 3 (`tools.jackson`) · RabbitMQ 4 · Testcontainers 2.0.5 (`testcontainers-rabbitmq`) ·
Awaitility (fourni par `spring-boot-starter-test`).

**Spec:** `docs/superpowers/specs/2026-08-25-evenements-metier-rabbitmq-design.md` — le
plan argumente depuis la spec ; l'exécutant lit les deux.

## Global Constraints

- Tout passe par Docker : `gtest` pour Gradle, `gfront` pour Node (voir `CLAUDE.md`). Définir
  `gtest` une fois par session. **`docker compose down` avant tout `gtest`** (verrou `.gradle/`).
- `make format-back` avant chaque commit ; ne pas se battre avec palantir-java-format. Le
  Javadoc n'est jamais reformaté : sa mise en forme est à la charge du rédacteur.
- Français partout (Javadoc, commentaires, messages, noms de méthodes de test en
  `snake_case`, messages de commit) ; noms de classes, méthodes de production et packages en
  anglais. Préfixes de commit en minuscule : `feat:`, `test:`, `conf:`, `docs:`.
- Le domaine (`shared/event/`, `knowledge/domain/`) n'importe jamais `org.springframework.*`.
- **Ne jamais annoter un handler `@Transactional`** ; toute exception métier hérite de
  `RuntimeException`.
- Pas de version pour les starters Spring ni pour les modules Testcontainers : le BOM Boot les
  porte. Dépendances ajoutées comme les existantes dans `build.gradle.kts`, en chaîne littérale.
- Tests d'intégration : `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`,
  jamais `@DataJpaTest`. Tester le port, pas l'adapter. Un test qui observe un commit n'est
  pas `@Transactional` et nettoie en `@AfterEach`.
- Branche : `feat/domain-events`, créée par le skill `worktree` (`../second-brain-domain-events`,
  `STACK_SUFFIX=-domain-events`). Le worktree a son propre volume Gradle
  `second-brain-gradle-home-domain-events` : premier `gtest` long.
- Image RabbitMQ : `rabbitmq:4-management-alpine` en développement, `rabbitmq:4-alpine` dans
  les tests (la console ne sert à rien à un test).

---

### Task 1: Dépendances, conteneur RabbitMQ de test, configuration de connexion

**Files:**
- Modify: `build.gradle.kts` (bloc `dependencies`)
- Modify: `src/test/java/xyz/sterenn/secondbrain/TestcontainersConfiguration.java`
- Modify: `src/main/resources/application.yml` (sous `spring:`)

**Interfaces:**
- Consumes: rien.
- Produces: un `ConnectionFactory` et un `RabbitTemplate` auto-configurés dans tout
  `@SpringBootTest`, vers un RabbitMQ Testcontainers ; les propriétés
  `spring.rabbitmq.*` lues depuis `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD`.

- [ ] **Step 1: Ajouter les dépendances**

Dans `build.gradle.kts`, après le bloc `// Notifications` :

```kotlin
    // Événements métier : publication et consommation sur RabbitMQ. Le transport est un
    // choix de la spec 2026-08-25 (décisions 2 à 5) ; le domaine ne le connaît pas.
    implementation("org.springframework.boot:spring-boot-starter-amqp")
```

Et dans le bloc `// Tests`, après `testcontainers-postgresql` :

```kotlin
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
```

- [ ] **Step 2: Ajouter le conteneur RabbitMQ à la configuration de test**

Remplacer le contenu de `TestcontainersConfiguration.java` par :

```java
package xyz.sterenn.secondbrain;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fournit une PostgreSQL et un RabbitMQ jetables pour les tests. {@code @ServiceConnection}
 * auto-configure la datasource et la connexion AMQP vers ces conteneurs.
 *
 * <p>RabbitMQ est là pour toute la suite, pas seulement pour les tests du socle
 * d'événements : Spring AMQP ne se connecte qu'au premier envoi, mais un dépôt de document
 * publie, et un conteneur de plus partagé coûte moins qu'une configuration de test à part.
 *
 * <p>{@code org.testcontainers.rabbitmq.RabbitMQContainer} et non
 * {@code org.testcontainers.containers.RabbitMQContainer} : Testcontainers 2 a déplacé la
 * classe, et Spring Boot 4 ne reconnaît l'ancienne que par une fabrique dépréciée.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        // Sans console de gestion : un test n'en a pas l'usage, et l'image est plus légère.
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    }
}
```

- [ ] **Step 3: Déclarer la connexion dans `application.yml`**

Sous `spring:`, après le bloc `mail:` :

```yaml
  rabbitmq:
    # Même statut que la datasource : le défaut sert le développement hors conteneur,
    # compose.yaml pose les vrais. Les identifiants par défaut de l'image officielle sont
    # guest/guest, et ils ne sont acceptés que depuis localhost — un déploiement pose les
    # siens par ces quatre variables.
    host: ${SPRING_RABBITMQ_HOST:localhost}
    port: ${SPRING_RABBITMQ_PORT:5672}
    username: ${SPRING_RABBITMQ_USERNAME:guest}
    password: ${SPRING_RABBITMQ_PASSWORD:guest}
    listener:
      simple:
        # Un message dont le traitement lève une exception est rejeté SANS remise en file.
        # Le défaut (true) le remettrait en tête de file et le retraiterait sans fin : un
        # PDF qui fait planter l'extracteur bloquerait le worker pour toujours. Pas de
        # dead-letter queue ni de retry (spec, décision 7) : un échec doit finir en FAILED
        # sur le document, pas être rejoué.
        default-requeue-rejected: false
```

- [ ] **Step 4: Vérifier que le contexte démarre avec les deux conteneurs**

Run: `docker compose down; gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"`
Expected: `BUILD SUCCESSFUL` ; dans `build/reports/tests/test/index.html` ou la sortie, une
ligne Testcontainers `Container rabbitmq:4-alpine started`.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add build.gradle.kts src/test/java/xyz/sterenn/secondbrain/TestcontainersConfiguration.java src/main/resources/application.yml
git commit -m "conf: ajoute Spring AMQP et un RabbitMQ Testcontainers à la suite"
```

---

### Task 2: Domaine — `DomainEvent`, `DomainEventPublisher`, `DocumentUploaded`

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/event/DomainEvent.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/event/DomainEventPublisher.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/event/DocumentUploaded.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/event/DocumentUploadedTest.java`

**Interfaces:**
- Consumes: rien.
- Produces: `interface DomainEvent { Instant occurredAt(); }` ;
  `interface DomainEventPublisher { void publish(DomainEvent event); }` ;
  `record DocumentUploaded(UUID documentId, UUID ownerId, Instant occurredAt) implements DomainEvent`,
  qui refuse tout composant `null` par `NullPointerException` avec un message français.

- [ ] **Step 1: Écrire le test qui échoue**

```java
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
```

- [ ] **Step 2: Vérifier qu'il échoue**

Run: `gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploadedTest"`
Expected: échec de compilation, `DocumentUploaded` et `DomainEvent` introuvables.

- [ ] **Step 3: Écrire le domaine**

`shared/event/DomainEvent.java` :

```java
package xyz.sterenn.secondbrain.shared.event;

import java.time.Instant;

/**
 * Fait métier survenu et acquis. Au passé, nommé par ce qui s'est passé
 * ({@code DocumentUploaded}), jamais par ce qu'on voudrait qu'il déclenche.
 *
 * <p>Sans import Spring, comme {@code shared/bus} : un contexte borné publie sans rien
 * savoir du transport. Les événements techniques de Spring ({@code ApplicationEvent}) sont
 * autre chose et ne passent pas par ici.
 *
 * <p>Un seul contrat, l'instant : c'est ce qu'un consommateur ou un journal veut toujours.
 * Pas d'identifiant d'événement — rien ne dédoublonne, voir la décision 3 de la spec.
 * Chaque contexte déclare ses événements en records dans {@code <contexte>/domain/event/}.
 */
public interface DomainEvent {

    Instant occurredAt();
}
```

`shared/event/DomainEventPublisher.java` :

```java
package xyz.sterenn.secondbrain.shared.event;

/**
 * Port sortant : annonce un événement métier au reste du système.
 *
 * <p>Depuis une transaction — toujours le cas depuis {@code SpringCommandBus} — l'annonce
 * ne part qu'au commit, et un rollback n'annonce rien. Hors transaction, elle part
 * immédiatement. C'est le handler qui publie, en dernière étape de son orchestration : la
 * place de l'appel dans la séquence n'a aucune importance transactionnelle, elle est
 * dernière pour se lire comme ce qu'elle est, une annonce.
 *
 * <p>Ce qui arrive à l'annonce après le commit n'est pas garanti par ce port (spec,
 * décision 3) : un événement que le transport n'a pas reçu est perdu.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
```

`knowledge/domain/event/DocumentUploaded.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Un document vient d'être déposé : sa ligne est écrite, son original conservé.
 *
 * <p>L'événement porte l'identifiant — ce que le consommateur relira — et le propriétaire,
 * pour router ou journaliser par compte sans relire. Rien d'autre : ni nom, ni format, ni
 * empreinte. Il dit <em>qu'il</em> s'est passé quelque chose, pas <em>quoi</em> en détail ;
 * le document en base fait foi.
 *
 * <p>Voyage en JSON sur le transport : les trois composants sont des types que Jackson lit
 * et écrit sans configuration, et le record se désérialise par ses paramètres.
 */
public record DocumentUploaded(UUID documentId, UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DocumentUploaded {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
    }
}
```

- [ ] **Step 4: Vérifier que le test passe**

Run: `gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploadedTest"`
Expected: 4 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/shared/event src/main/java/xyz/sterenn/secondbrain/knowledge/domain/event src/test/java/xyz/sterenn/secondbrain/knowledge/domain/event
git commit -m "feat: pose le port DomainEventPublisher et l'événement DocumentUploaded"
```

---

### Task 3: Nommage des événements et convertisseur JSON

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/event/amqp/DomainEventNames.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/event/amqp/DomainEventRegistration.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/event/amqp/AmqpConfiguration.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeMessagingConfiguration.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/shared/event/amqp/DomainEventNamesTest.java`

**Interfaces:**
- Consumes: `DomainEvent`, `DocumentUploaded` (Task 2).
- Produces: `DomainEventNames.of(Class<? extends DomainEvent>) : String` →
  `knowledge.DocumentUploaded` ; `DomainEventNames.mappingOf(List<Class<? extends DomainEvent>>) : Map<String, Class<?>>` ;
  `record DomainEventRegistration(List<Class<? extends DomainEvent>> types)` — un bean par
  contexte ; `AmqpConfiguration.EVENTS_EXCHANGE = "second-brain.events"` ; un bean
  `MessageConverter` dont l'en-tête `__TypeId__` porte le nom de `DomainEventNames`.

- [ ] **Step 1: Écrire le test qui échoue**

```java
package xyz.sterenn.secondbrain.shared.event.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

class DomainEventNamesTest {

    @Test
    void nomme_par_le_contexte_borne_et_la_classe() {
        assertThat(DomainEventNames.of(DocumentUploaded.class)).isEqualTo("knowledge.DocumentUploaded");
    }

    @Test
    void refuse_un_evenement_hors_d_un_contexte_borne() {
        // Ce record est déclaré ici, donc dans shared.event.amqp : `shared` n'est pas un
        // contexte borné, un événement n'a rien à y faire.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DomainEventNames.of(HorsContexte.class))
                .withMessageContaining(HorsContexte.class.getName());
    }

    @Test
    void construit_la_table_des_noms_connus() {
        assertThat(DomainEventNames.mappingOf(List.of(DocumentUploaded.class)))
                .containsExactly(java.util.Map.entry("knowledge.DocumentUploaded", DocumentUploaded.class));
    }

    @Test
    void refuse_deux_classes_du_meme_nom() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DomainEventNames.mappingOf(List.of(DocumentUploaded.class, DocumentUploaded.class)))
                .withMessageContaining("knowledge.DocumentUploaded");
    }

    record HorsContexte(Instant occurredAt) implements DomainEvent {}
}
```

- [ ] **Step 2: Vérifier qu'il échoue**

Run: `gtest test --tests "xyz.sterenn.secondbrain.shared.event.amqp.DomainEventNamesTest"`
Expected: échec de compilation, `DomainEventNames` introuvable.

- [ ] **Step 3: Écrire `DomainEventNames` et `DomainEventRegistration`**

`shared/event/amqp/DomainEventNames.java` :

```java
package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Le nom d'un événement sur le transport : {@code <contexte>.<Classe>}, soit
 * {@code knowledge.DocumentUploaded} pour
 * {@code xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded}.
 *
 * <p>Ce nom sert de clé de routage sur l'exchange et d'en-tête de type sur le message —
 * jamais le nom qualifié de la classe : renommer un package ne casse pas les messages en
 * vol, et le nom se lit dans la console du broker. Il est dérivé ici, dans l'adapter, pour
 * que le domaine ne nomme rien (spec, décision 4).
 *
 * <p>{@code shared} et {@code config} ne sont pas des contextes bornés : un événement qui y
 * vivrait est refusé, il n'appartient à personne.
 */
public final class DomainEventNames {

    private static final String ROOT = "xyz.sterenn.secondbrain";
    private static final Set<String> NOT_A_CONTEXT = Set.of("shared", "config");

    private DomainEventNames() {
        // classe utilitaire
    }

    /**
     * @throws IllegalArgumentException si la classe n'est pas dans un contexte borné du projet
     */
    public static String of(Class<? extends DomainEvent> type) {
        String pkg = type.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            throw new IllegalArgumentException(type.getName() + " n'est pas dans un contexte borné de " + ROOT);
        }
        String context = pkg.substring(ROOT.length() + 1).split("\\.")[0];
        if (NOT_A_CONTEXT.contains(context)) {
            throw new IllegalArgumentException(
                    type.getName() + " est dans " + context + ", qui n'est pas un contexte borné");
        }
        return context + "." + type.getSimpleName();
    }

    /**
     * La table nom → classe des événements connus, pour le convertisseur de messages.
     *
     * @throws IllegalStateException si deux classes portent le même nom
     */
    public static Map<String, Class<?>> mappingOf(List<Class<? extends DomainEvent>> types) {
        Map<String, Class<?>> mapping = new HashMap<>();
        for (Class<? extends DomainEvent> type : types) {
            String name = of(type);
            Class<?> previous = mapping.put(name, type);
            if (previous != null) {
                throw new IllegalStateException(
                        "Deux événements portent le nom " + name + " : " + previous.getName() + " et " + type.getName());
            }
        }
        return mapping;
    }
}
```

`shared/event/amqp/DomainEventRegistration.java` :

```java
package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.List;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Les événements qu'un contexte borné sait publier ou consommer. Chaque contexte en
 * déclare un en {@code @Bean} dans son infrastructure ; {@link AmqpConfiguration} les
 * collecte pour construire la table des noms du convertisseur.
 *
 * <p>Déclarés et non scannés : un événement absent de toute déclaration échoue à la
 * désérialisation avec un message qui porte son nom, plutôt que par un
 * {@code ClassNotFoundException} sur un nom qualifié.
 */
public record DomainEventRegistration(List<Class<? extends DomainEvent>> types) {}
```

- [ ] **Step 4: Vérifier que le test passe**

Run: `gtest test --tests "xyz.sterenn.secondbrain.shared.event.amqp.DomainEventNamesTest"`
Expected: 4 tests, `BUILD SUCCESSFUL`.

- [ ] **Step 5: Écrire `AmqpConfiguration` et la déclaration de `knowledge`**

`shared/event/amqp/AmqpConfiguration.java` :

```java
package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.List;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Topologie et sérialisation communes à tous les événements métier.
 *
 * <p>Un seul exchange <em>topic</em> : un consommateur futur pourra écouter
 * {@code knowledge.#} sans rien redéclarer côté publication. Chaque consommateur déclare
 * <strong>sa</strong> queue et son binding dans son propre contexte — une queue est une
 * intention de consommation, elle appartient à celui qui consomme.
 *
 * <p>Le convertisseur est celui que Spring Boot donne au {@code RabbitTemplate} et aux
 * listeners : un seul bean {@link MessageConverter}, et les deux côtés parlent la même
 * langue. Les déclarations d'événements sont collectées par {@link ObjectProvider} pour la
 * même raison que les handlers dans {@code BusConfiguration} : le contexte doit démarrer
 * même si aucun contexte borné n'en déclare.
 */
@Configuration
public class AmqpConfiguration {

    public static final String EVENTS_EXCHANGE = "second-brain.events";

    @Bean
    public TopicExchange domainEventsExchange() {
        // durable, non auto-delete : l'exchange survit au redémarrage du broker.
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter domainEventMessageConverter(ObjectProvider<DomainEventRegistration> registrations) {
        List<Class<? extends DomainEvent>> types =
                registrations.stream().flatMap(r -> r.types().stream()).toList();

        // L'en-tête __TypeId__ porte le nom de DomainEventNames dans les deux sens : à
        // l'envoi par la table inversée, à la réception par la table directe. Un nom absent
        // de la table n'est jamais résolu par Class.forName — les paquets de confiance du
        // mapper (java.lang, java.util) ne contiennent aucun événement.
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(DomainEventNames.mappingOf(types));

        // Constructeur sans argument : le convertisseur construit son JsonMapper Jackson 3
        // avec le module java.time, et Jackson 3 écrit les Instant en ISO-8601 par défaut.
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
```

`knowledge/infrastructure/messaging/KnowledgeMessagingConfiguration.java` (Task 6 y ajoutera
la queue et son binding) :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

/**
 * Ce que le contexte {@code knowledge} met sur le transport : ses événements, et la queue
 * par laquelle il consomme.
 */
@Configuration
public class KnowledgeMessagingConfiguration {

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(DocumentUploaded.class));
    }
}
```

- [ ] **Step 6: Vérifier que le contexte démarre toujours**

Run: `gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/shared/event/amqp src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging src/test/java/xyz/sterenn/secondbrain/shared/event/amqp
git commit -m "feat: nomme les événements métier et configure leur sérialisation AMQP"
```

---

### Task 4: `AmqpDomainEventPublisher` — publication après commit

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/shared/event/amqp/AmqpDomainEventPublisher.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/shared/event/amqp/AmqpDomainEventPublisherTest.java`

**Interfaces:**
- Consumes: `DomainEventPublisher`, `DomainEventNames`, `AmqpConfiguration.EVENTS_EXCHANGE`.
- Produces: le bean `DomainEventPublisher` injectable partout ; les messages arrivent sur
  `second-brain.events` avec la clé `knowledge.DocumentUploaded` et l'en-tête
  `__TypeId__` = `knowledge.DocumentUploaded`.

- [ ] **Step 1: Écrire le test qui échoue**

```java
package xyz.sterenn.secondbrain.shared.event.amqp;

import static org.assertj.core.api.Assertions.assertThat;
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
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Le contrat du port : l'annonce part au commit, jamais avant, jamais après un rollback.
 *
 * <p>Pas de {@code @Transactional} sur la classe : le test observe des commits, une
 * transaction englobante les masquerait. Rien n'est écrit en base ; la seule chose à
 * nettoyer est la queue d'observation, déclarée exclusive et auto-effacée.
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
        amqpAdmin.declareQueue(new Queue(OBSERVATION, false, false, true));
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
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("knowledge.DocumentUploaded");
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("__TypeId__", "knowledge.DocumentUploaded");
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
}
```

- [ ] **Step 2: Vérifier qu'il échoue**

Run: `gtest test --tests "xyz.sterenn.secondbrain.shared.event.amqp.AmqpDomainEventPublisherTest"`
Expected: le contexte ne démarre pas — `No qualifying bean of type 'DomainEventPublisher'`.

- [ ] **Step 3: Écrire l'adapter**

```java
package xyz.sterenn.secondbrain.shared.event.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Adapter du port {@link DomainEventPublisher} sur RabbitMQ.
 *
 * <p>Depuis une transaction, l'envoi est différé à {@code afterCommit} : la base a commité,
 * l'événement peut être annoncé. Un rollback ne l'annonce jamais — pas d'événement fantôme
 * désignant une ligne qui n'existe pas. C'est la garantie qui compte pour un consommateur
 * qui va relire le document.
 *
 * <p>L'inverse n'est pas garanti : si le broker est injoignable dans {@code afterCommit},
 * l'écriture est acquise et l'événement est perdu. L'exception est journalisée, pas
 * propagée — elle ne peut plus annuler le commit, et elle ne doit pas faire échouer une
 * requête dont l'écriture a réussi. Pas d'outbox, pas de rattrapage : décision 3 de la
 * spec, écart assumé dans CLAUDE.md.
 *
 * <p>Hors transaction, l'envoi est immédiat et une panne du broker remonte à l'appelant :
 * il n'y a rien d'acquis à protéger.
 */
@Component
public class AmqpDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AmqpDomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public AmqpDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(DomainEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    send(event);
                } catch (AmqpException e) {
                    log.error(
                            "Événement {} perdu : le broker n'a pas pu être joint après le commit ({})",
                            DomainEventNames.of(event.getClass()),
                            e.getMessage(),
                            e);
                }
            }
        });
    }

    private void send(DomainEvent event) {
        String name = DomainEventNames.of(event.getClass());
        rabbitTemplate.convertAndSend(AmqpConfiguration.EVENTS_EXCHANGE, name, event);
        log.debug("Événement {} publié", name);
    }
}
```

- [ ] **Step 4: Vérifier que le test passe**

Run: `gtest test --tests "xyz.sterenn.secondbrain.shared.event.amqp.AmqpDomainEventPublisherTest"`
Expected: 3 tests, `BUILD SUCCESSFUL`. Si `publie_immediatement_hors_transaction` échoue sur
l'en-tête `__TypeId__` (nom qualifié au lieu de `knowledge.DocumentUploaded`), c'est que le
`MessageConverter` de `AmqpConfiguration` n'est pas celui du `RabbitTemplate` : vérifier qu'il
n'existe qu'un seul bean `MessageConverter` dans le contexte.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/shared/event/amqp/AmqpDomainEventPublisher.java src/test/java/xyz/sterenn/secondbrain/shared/event/amqp/AmqpDomainEventPublisherTest.java
git commit -m "feat: publie les événements métier sur RabbitMQ après le commit"
```

---

### Task 5: Le dépôt annonce `DocumentUploaded`

**Files:**
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/UploadDocumentHandler.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/UploadDocumentAnnouncementTest.java`

**Interfaces:**
- Consumes: `DomainEventPublisher` (Task 4), `DocumentUploaded` (Task 2), `Clock`
  (`ClockConfiguration`).
- Produces: chaque dépôt commité émet un `DocumentUploaded` portant l'identifiant du
  document créé et son propriétaire.

- [ ] **Step 1: Écrire le test qui échoue**

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;

/**
 * Le premier scénario du socle : un dépôt réussi est annoncé, par le chemin réel — le bus,
 * sa transaction, son commit.
 *
 * <p>Pas de {@code @Transactional} : l'annonce ne part qu'au commit, une transaction de test
 * l'empêcherait de partir. Le compte et le document sont donc réellement écrits, et effacés
 * en {@code @AfterEach} — la clé étrangère en cascade emporte le document avec le compte,
 * le disque se vide à part.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
class UploadDocumentAnnouncementTest {

    private static final String EMAIL = "gaston@exemple.fr";
    private static final String OBSERVATION = "test.observation.depot";
    private static final byte[] CONTENU = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${secondbrain.storage.originals-path}")
    private String cheminDesOriginaux;

    private UUID compte;

    @BeforeEach
    void prepare_un_compte_et_une_queue_d_observation() {
        recordingNotificationSender.clear();
        compte = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, "chevalpile42");
        amqpAdmin.declareQueue(new Queue(OBSERVATION, false, false, true));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION,
                Binding.DestinationType.QUEUE,
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.DocumentUploaded",
                null));
    }

    @AfterEach
    void efface_ce_qui_a_ete_commite() {
        amqpAdmin.deleteQueue(OBSERVATION);
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.videLesOriginaux(cheminDesOriginaux);
    }

    @Test
    void annonce_le_document_depose_une_fois_le_depot_commite() {
        commandBus.dispatch(new UploadDocument(compte, "rapport.txt", CONTENU));

        Object recu = rabbitTemplate.receiveAndConvert(OBSERVATION, Duration.ofSeconds(5).toMillis());

        Document document = documentRepository
                .findByOwnerIdAndChecksum(compte, Checksum.of(CONTENU))
                .orElseThrow();
        assertThat(recu).isInstanceOf(DocumentUploaded.class);
        DocumentUploaded evenement = (DocumentUploaded) recu;
        assertThat(evenement.documentId()).isEqualTo(document.getId());
        assertThat(evenement.ownerId()).isEqualTo(compte);
        assertThat(evenement.occurredAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Vérifier qu'il échoue**

Run: `gtest test --tests "xyz.sterenn.secondbrain.knowledge.application.command.UploadDocumentAnnouncementTest"`
Expected: FAIL — `recu` est `null` (rien n'arrive dans les 5 s), donc
`assertThat(recu).isInstanceOf(...)` échoue.

- [ ] **Step 3: Faire publier le handler**

Remplacer `UploadDocumentHandler.java` par :

```java
package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Orchestre le dépôt : reconnaissance du format, calcul de l'empreinte, refus du doublon,
 * écriture, conservation du fichier d'origine, puis annonce.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit.
 *
 * <p><strong>L'ordre des étapes est un choix.</strong> Le contrôle du doublon en mémoire
 * d'abord, parce qu'il rend un refus <em>désignant</em> le document existant, ce que la
 * violation de contrainte ne saurait pas faire. L'écriture en base ensuite, avec son flush,
 * parce que c'est elle qui tranche en cas de dépôts simultanés. Le fichier après, parce
 * qu'un système de fichiers ne participe à aucune transaction : écrit avant, il survivrait
 * à un rollback en désignant une ligne qui n'existe pas.
 *
 * <p>L'annonce en tout dernier. Elle ne prend effet qu'au commit — le port la diffère —,
 * donc sa place dans la séquence n'a aucune importance transactionnelle : elle est dernière
 * pour se lire comme ce qu'elle est, une annonce de ce qui vient d'être fait. Un dépôt
 * refusé ou annulé n'annonce rien.
 */
@Component
public class UploadDocumentHandler implements CommandHandler<UploadDocument> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public UploadDocumentHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Override
    public void handle(UploadDocument command) {
        // Lève UnsupportedDocumentFormatException, message énonçant les formats acceptés.
        DocumentFormat format = DocumentFormat.fromFilename(command.filename());

        // C'est le contenu qui fait foi : le même fichier renommé reste le même document.
        Checksum checksum = Checksum.of(command.content());

        Optional<Document> existant = documentRepository.findByOwnerIdAndChecksum(command.ownerId(), checksum);
        if (existant.isPresent()) {
            throw new DuplicateDocumentException(existant.get().getId());
        }

        Document document = documentRepository.save(
                Document.upload(command.ownerId(), command.filename(), format, checksum, command.content().length));

        documentStorage.store(document.getId(), command.content());

        domainEventPublisher.publish(new DocumentUploaded(document.getId(), document.getOwnerId(), clock.instant()));
    }
}
```

- [ ] **Step 4: Vérifier que le test passe, et que les tests du dépôt n'ont pas bougé**

Run: `gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"`
Expected: `BUILD SUCCESSFUL`, `UploadDocumentAnnouncementTest` vert, les tests
`@Transactional` de `UploadDocumentControllerTest` toujours verts (leur rollback n'envoie
rien, et n'a pas à le faire).

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/UploadDocumentHandler.java src/test/java/xyz/sterenn/secondbrain/knowledge/application/command/UploadDocumentAnnouncementTest.java
git commit -m "feat: annonce DocumentUploaded à chaque dépôt commité"
```

---

### Task 6: Le rôle worker — profil, queue, listener

**Files:**
- Create: `src/main/resources/application-worker.yml`
- Modify: `src/main/java/xyz/sterenn/secondbrain/config/SecurityConfig.java` (annotation + Javadoc)
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeMessagingConfiguration.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/DocumentUploadedListener.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/DocumentUploadedListenerTest.java`

**Interfaces:**
- Consumes: `DocumentUploaded`, `AmqpConfiguration.EVENTS_EXCHANGE`, le `MessageConverter`
  (Task 3).
- Produces: la queue durable `knowledge.extraction` liée à `knowledge.DocumentUploaded`,
  déclarée dans les deux rôles ; sous le profil `worker`, un processus sans Tomcat qui
  consomme cette queue et journalise
  `Événement knowledge.DocumentUploaded reçu pour le document <uuid>` — **ligne que le plan
  d'extraction remplacera par `commandBus.dispatch(new ExtractDocumentText(...))`**.

- [ ] **Step 1: Écrire le test qui échoue**

```java
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
        assertThat(applicationContext.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DocumentUploadedListener.class)).hasSize(1);
    }

    @Test
    void recoit_l_evenement_publie(CapturedOutput sortie) {
        UUID document = UUID.randomUUID();

        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.DocumentUploaded",
                new DocumentUploaded(document, UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z")));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(sortie)
                        .contains("Événement knowledge.DocumentUploaded reçu pour le document " + document));
    }
}
```

- [ ] **Step 2: Vérifier qu'il échoue**

Run: `gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.messaging.DocumentUploadedListenerTest"`
Expected: échec de compilation, `DocumentUploadedListener` introuvable. (Sans le profil,
le contexte échouerait aussi : `SecurityConfig` réclame `HttpSecurity`, absent sans servlet.)

- [ ] **Step 3: Écrire le profil et effacer la sécurité HTTP du worker**

`src/main/resources/application-worker.yml` :

```yaml
# Profil `worker` : le même jar, sans serveur HTTP. Activé par SPRING_PROFILES_ACTIVE=worker
# (`dev,worker` dans compose.yaml). Sans profil, le processus est l'API — rien ne change pour
# un déploiement existant.
#
# Couper Tomcat retire les contrôleurs du routage, Swagger et l'actuator HTTP. Ce qui reste
# est commun aux deux rôles : bus, JPA, Flyway, stockage, mail, publication d'événements. Et
# c'est ici, et seulement ici, que les listeners `@Profile("worker")` existent.
spring:
  main:
    web-application-type: none
```

Dans `SecurityConfig.java`, ajouter l'import `org.springframework.context.annotation.Profile`,
annoter la classe :

```java
@Configuration
@Profile("!worker")
public class SecurityConfig {
```

et ajouter ce paragraphe à la fin du Javadoc de classe (avant `*/`) :

```java
 *
 * <p>Absente du rôle {@code worker} : cette configuration réclame {@code HttpSecurity}, qui
 * n'existe pas sans servlet, et un processus qui n'écoute rien n'a rien à protéger.
 * {@code JwtConfiguration}, elle, reste dans les deux rôles — {@code JwtAccessTokenIssuer}
 * en dépend et n'a rien de propre au web.
```

- [ ] **Step 4: Déclarer la queue et écrire le listener**

Remplacer `KnowledgeMessagingConfiguration.java` par :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventNames;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

/**
 * Ce que le contexte {@code knowledge} met sur le transport : ses événements, et la queue
 * par laquelle il consomme.
 *
 * <p>La queue est déclarée dans les deux rôles, pas seulement dans le worker : Spring AMQP
 * déclare à la première connexion, les déclarations sont idempotentes, et l'API démarrée
 * seule ne doit pas publier dans un exchange sans queue liée — le message serait perdu sans
 * bruit. Durable : elle survit au redémarrage du broker, avec ses messages non consommés.
 */
@Configuration
public class KnowledgeMessagingConfiguration {

    public static final String EXTRACTION_QUEUE = "knowledge.extraction";

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(DocumentUploaded.class));
    }

    @Bean
    public Queue extractionQueue() {
        return new Queue(EXTRACTION_QUEUE, true);
    }

    @Bean
    public Binding extractionBinding(Queue extractionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(extractionQueue)
                .to(domainEventsExchange)
                .with(DomainEventNames.of(DocumentUploaded.class));
    }
}
```

`DocumentUploadedListener.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;

/**
 * Adapter entrant : reçoit {@link DocumentUploaded} depuis la queue d'extraction.
 *
 * <p>Un listener, une queue, une commande — la règle « une classe de contrôleur, un
 * mapping » vaut ici aussi. Il désérialise et dispatche ; aucune règle métier.
 *
 * <p>{@code @Profile("worker")} : l'API publie, elle ne consomme jamais. Une exception
 * levée ici rejette le message sans remise en file
 * ({@code default-requeue-rejected=false} dans {@code application.yml}) : un échec doit
 * finir en {@code FAILED} sur le document, pas être rejoué.
 */
@Component
@Profile("worker")
public class DocumentUploadedListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadedListener.class);

    @RabbitListener(queues = KnowledgeMessagingConfiguration.EXTRACTION_QUEUE)
    public void on(DocumentUploaded event) {
        // Tant qu'aucune commande d'extraction n'existe, recevoir se constate au journal.
        // Le plan d'extraction remplace cette ligne par
        // commandBus.dispatch(new ExtractDocumentText(event.documentId())).
        log.info("Événement knowledge.DocumentUploaded reçu pour le document {}", event.documentId());
    }
}
```

- [ ] **Step 5: Vérifier que le test passe, et que le rôle API n'a pas bougé**

Run: `gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.messaging.*" --tests "xyz.sterenn.secondbrain.config.*" --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"`
Expected: `BUILD SUCCESSFUL`. Dans la sortie du contexte worker, une ligne
`Using generated security password` peut apparaître : c'est l'auto-configuration Spring
Security qui pose un utilisateur en mémoire faute de `SecurityFilterChain` — inoffensif sans
serveur HTTP, et pas une raison d'exclure le starter du worker.

- [ ] **Step 6: Formater et committer**

```bash
make format-back
git add src/main/resources/application-worker.yml src/main/java/xyz/sterenn/secondbrain/config/SecurityConfig.java src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging
git commit -m "feat: rôle worker par profil, queue d'extraction et listener DocumentUploaded"
```

---

### Task 7: La pile de développement — `rabbitmq`, `worker`, ports, skill `worktree`

**Files:**
- Modify: `compose.yaml`
- Modify: `.env.example`
- Modify: `.gitignore`
- Modify: `.claude/skills/worktree/SKILL.md:57`
- Modify: `.claude/skills/worktree/scripts/create-worktree.sh:51,63-64,76,107-110,150-152`

**Interfaces:**
- Consumes: le profil `worker` (Task 6).
- Produces: `docker compose up --build` démarre RabbitMQ, l'API et le worker ; un dépôt fait
  dans le navigateur apparaît dans `docker compose logs -f worker` ; console RabbitMQ sur
  <http://localhost:15672> (`guest`/`guest`). C'est la **validation de la décision 9** de la
  spec.

- [ ] **Step 1: Ajouter le service `rabbitmq`**

Dans `compose.yaml`, après le service `mailpit` :

```yaml
  rabbitmq:
    image: rabbitmq:4-management-alpine
    ports:
      # AMQP pour l'application, console de gestion pour voir passer les messages. Même
      # statut que Mailpit : un outil de développement, pas routé par Traefik.
      - "${RABBITMQ_PORT:-5672}:5672"
      - "${RABBITMQ_WEB_PORT:-15672}:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 5s
      timeout: 5s
      retries: 12
```

- [ ] **Step 2: Brancher `app` sur RabbitMQ**

Dans le service `app`, ajouter à `environment` :

```yaml
      SPRING_RABBITMQ_HOST: rabbitmq
```

et à `depends_on` :

```yaml
      rabbitmq:
        condition: service_healthy
```

- [ ] **Step 3: Ajouter le service `worker`**

Après le service `app` :

```yaml
  worker:
    build:
      context: .
      dockerfile: Dockerfile.dev
    # Même image que `app`, second processus, rôle décidé par le profil. Pas de compilateur
    # continu ici : celui de `app` recompile vers build/classes, que DevTools surveille dans
    # ce conteneur aussi — un .java modifié redémarre les deux. Le --project-cache-dir
    # propre évite le verrou Gradle que `app` tient sur .gradle/ (spec, décision 9).
    command: ["./gradlew", "--no-daemon", "--project-cache-dir", ".gradle-worker", "bootRun"]
    environment:
      SPRING_PROFILES_ACTIVE: dev,worker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB:-second_brain}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-second_brain}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-second_brain}
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: 1025
      SPRING_RABBITMQ_HOST: rabbitmq
      SECONDBRAIN_BASE_URL: http://localhost:${HTTP_PORT:-8080}
      SECONDBRAIN_JWT_SECRET: ${JWT_SECRET:-secret-de-developpement-second-brain-32-octets}
      SECONDBRAIN_ORIGINALS_PATH: /data/originals
    volumes:
      - ./:/workspace
      - gradle-cache:/workspace/.gradle-cache
      - originals:/data/originals
    depends_on:
      db:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      app:
        condition: service_started
```

- [ ] **Step 4: `.env.example` et `.gitignore`**

Dans `.env.example`, après `MAILPIT_WEB_PORT=8025` et avant le commentaire sur le décalage :

```bash
RABBITMQ_PORT=5672
RABBITMQ_WEB_PORT=15672
```

et corriger la phrase qui suit : « ces **six** ports sont décalés du même indice
(8081/5433/1026/8026/5673/15673 pour le premier, et ainsi de suite) ». Dans le bloc de
variables lues par l'application, ajouter :

```bash
# SPRING_RABBITMQ_HOST=rabbitmq
# SPRING_RABBITMQ_PORT=5672
# SPRING_RABBITMQ_USERNAME=guest
# SPRING_RABBITMQ_PASSWORD=guest
```

Dans `.gitignore`, sous `# Gradle`, après `.gradle-cache/` :

```
.gradle-worker/
```

- [ ] **Step 5: Le skill `worktree` apprend deux ports**

`SKILL.md` ligne 57, remplacer la cellule par :
``Un bloc de ports d'indice N : `8080+N`, `5432+N`, `1025+N`, `8025+N`, `5672+N`, `15672+N` ``.

`create-worktree.sh` :
- ligne 51, le commentaire : `# Le bloc d'indice N donne 8080+N, 5432+N, 1025+N, 8025+N, 5672+N, 15672+N.`
- ligne 76, la boucle de vérification des ports libres :
  ```sh
  for port in $((8080 + candidate)) $((5432 + candidate)) $((1025 + candidate)) $((8025 + candidate)) $((5672 + candidate)) $((15672 + candidate)); do
  ```
- après la ligne 110 (`MAILPIT_WEB_PORT`), deux expressions `sed` de plus :
  ```sh
    -e "s/^RABBITMQ_PORT=.*/RABBITMQ_PORT=$((5672 + index))/" \
    -e "s/^RABBITMQ_WEB_PORT=.*/RABBITMQ_WEB_PORT=$((15672 + index))/" \
  ```
- après la ligne 152 (`PostgreSQL`), dans le récapitulatif affiché :
  ```sh
    RabbitMQ    http://localhost:$((15672 + index))  (console, guest/guest)
  ```

- [ ] **Step 6: Valider la décision 9 sur la vraie pile**

```bash
docker compose up --build -d
docker compose logs -f worker
```

Expected, dans l'ordre : le worker démarre (`Started SecondBrainApplication` sans ligne
`Tomcat started on port`), aucune erreur `Timeout waiting to lock`. Puis, dans le
navigateur sur <http://localhost:8080>, se connecter et déposer un fichier `.txt` : la
ligne `Événement knowledge.DocumentUploaded reçu pour le document …` apparaît dans les
logs du worker en moins d'une seconde. Enfin, modifier un commentaire dans
`DocumentUploadedListener.java` : le worker redémarre (DevTools), et un second dépôt est
reçu.

Si le worker bloque sur un verrou Gradle malgré `--project-cache-dir`, ou si les deux
compilations se marchent dessus dans `build/` : **appliquer le repli de la spec** —
supprimer le service `worker`, passer `app` en `SPRING_PROFILES_ACTIVE: dev,worker`,
remplacer le commentaire du service par « en développement, un seul processus porte les
deux rôles ; la séparation n'est exercée qu'en production », et le noter dans la
décision 9 de la spec comme repli pris.

Vérifier aussi la console : <http://localhost:15672>, `guest`/`guest`, onglet *Queues* —
`knowledge.extraction` existe, 0 message en attente après consommation.

```bash
docker compose down
```

- [ ] **Step 7: Committer**

```bash
git add compose.yaml .env.example .gitignore .claude/skills/worktree
git commit -m "conf: ajoute RabbitMQ et le conteneur worker à la pile de développement"
```

---

### Task 8: Documentation — `CLAUDE.md`, règles backend, README, spec

**Files:**
- Modify: `CLAUDE.md` (arbre d'architecture, nouvelle section après « La transaction vit
  dans le bus », écart n° 12, nouvel écart n° 22, section « Stack et versions », section
  « Commandes »)
- Modify: `.claude/rules/backend.md` (nouvelle sous-section après « Bus, commandes et queries »)
- Modify: `README.md` (« Stack », tableau des URL, « Build de production »)
- Modify: `docs/superpowers/specs/2026-08-25-evenements-metier-rabbitmq-design.md`
  (décision 9 si le repli a été pris)

**Interfaces:**
- Consumes: tout ce qui précède.
- Produces: un lecteur de `CLAUDE.md` sait qu'un événement se publie par le port, ne part
  qu'au commit, se consomme dans le worker, et ce qui n'est pas garanti.

- [ ] **Step 1: `CLAUDE.md` — l'arbre**

Dans l'arbre sous « ## Architecture », après le bloc `shared/`, remplacer par :

```
├── shared/
│   ├── bus/                 socle CQRS, aucune dépendance métier
│   ├── event/               DomainEvent, port DomainEventPublisher — sans Spring
│   │   └── amqp/            ADAPTER RabbitMQ : publication après commit, nommage,
│   │                        convertisseur JSON, exchange
│   └── web/                 formes d'erreur communes à toutes les routes
```

Dans `knowledge/`, ajouter `│   │   └── event/           DocumentUploaded` sous `domain/`
après `exception/`, et sous `infrastructure/` :

```
│       └── messaging/       ADAPTER entrant : queue knowledge.extraction, listener
│                            DocumentUploaded (profil worker), catalogue des événements
```

- [ ] **Step 2: `CLAUDE.md` — la section**

Après la section « ### La transaction vit dans le bus », insérer :

```markdown
### Les événements métier (`shared/event`) et le rôle worker

Un handler qui a quelque chose à annoncer publie un **événement métier** par le port
`DomainEventPublisher` — en dernière étape, et explicitement : `UploadDocumentHandler`
publie `DocumentUploaded` après avoir conservé l'original. Les événements sont des records
au passé dans `<contexte>/domain/event/`, avec un seul contrat (`occurredAt`), sans import
Spring. **Ce ne sont pas des `ApplicationEvent`** : les événements techniques de Spring
restent techniques.

L'adapter (`shared/event/amqp/`) n'envoie qu'**après le commit** de la transaction ouverte
par le bus : un rollback n'annonce rien. L'inverse n'est pas garanti — voir l'écart n° 22.
Le transport est RabbitMQ : un exchange topic `second-brain.events`, une clé de routage
dérivée de la classe (`knowledge.DocumentUploaded`), un corps JSON, et cette même chaîne en
en-tête de type — jamais le nom qualifié. Le domaine ne nomme rien.

**La consommation vit dans un processus à part.** Le profil Spring `worker` coupe Tomcat
(`spring.main.web-application-type=none`, donc ni contrôleurs, ni Swagger, ni actuator
HTTP), efface `SecurityConfig` (`@Profile("!worker")`) et fait exister les listeners
(`@Profile("worker")`). Sans profil, le processus est l'API. Même image, même jar : en
développement, `compose.yaml` lance `app` et `worker` ; en production, deux déploiements
Coolify de la même image, le second avec `SPRING_PROFILES_ACTIVE=worker`.

Un listener (`<contexte>/infrastructure/messaging/`) est un adapter entrant au même titre
qu'un contrôleur : une classe, une queue, une commande dispatchée sur le bus, aucune règle
métier. Chaque consommateur déclare **sa** queue et son binding, dans les deux rôles. Une
exception dans un listener rejette le message **sans remise en file**
(`default-requeue-rejected=false`) : sans ce réglage, un message toxique tournerait en
boucle. Pas de dead-letter queue, pas de retry : un échec doit finir en `FAILED` sur le
document, pas être rejoué.

Les tests du socle observent des commits : ils ne sont pas `@Transactional` et nettoient
en `@AfterEach`. Le rôle worker se teste avec `@ActiveProfiles("worker")` et
`webEnvironment = NONE`.
```

- [ ] **Step 3: `CLAUDE.md` — les écarts**

Écart n° 12, remplacer la dernière phrase par : « Deux configurations de routage doivent
donc rester cohérentes à la main — et depuis les événements métier, le service RabbitMQ et
le déploiement du worker (même image, `SPRING_PROFILES_ACTIVE=worker`, variables
`SPRING_RABBITMQ_*`) vivent eux aussi dans Coolify, hors du dépôt. »

Après l'écart n° 21, ajouter :

```markdown
22. **La publication d'un événement métier n'est pas garantie.** L'adapter envoie dans
    `afterCommit` : la base a commité, et si RabbitMQ est injoignable à cet instant,
    l'écriture est acquise mais l'événement est perdu — un document resterait `PENDING`
    sans que rien ne le reprenne. Les deux parades (outbox en base avec relais, balayage
    périodique des `PENDING`) ont été étudiées et écartées : on fait confiance au broker.
    Condition de bascule : un événement sans état observable derrière lui, ou une perte
    constatée. `DomainEventPublisher` est un port ; l'outbox serait une implémentation de
    plus, aucun handler ne changerait.
```

- [ ] **Step 4: `CLAUDE.md` — stack et commandes**

Dans « ## Stack et versions », ligne **Back**, ajouter « Spring AMQP · RabbitMQ 4 » après
« PostgreSQL 17 », et dans **Développement** : « RabbitMQ 4 avec sa console de gestion sur
<http://localhost:15672> (`guest`/`guest`), et un conteneur `worker` de la même image que
`app`. »

Dans « ## Commandes », après le paragraphe sur Mailpit, ajouter :

```markdown
Le worker est un conteneur à part : `docker compose logs -f worker` montre les événements
reçus. Il n'a pas de compilateur continu — celui de `app` recompile, DevTools redémarre les
deux. Son `--project-cache-dir` propre (`.gradle-worker/`, ignoré) est ce qui lui permet de
partager le bind mount sans se battre pour le verrou Gradle de `app`.
```

- [ ] **Step 5: `.claude/rules/backend.md`**

Après la sous-section « ## Bus, commandes et queries », insérer :

```markdown
## Événements métier

- Un événement est un **record au passé** dans `<contexte>/domain/event/`, implémente
  `DomainEvent` (`shared/event/`), n'importe rien de Spring, et porte des identifiants —
  pas l'état. Le consommateur relit.
- **C'est le handler qui publie**, par le port `DomainEventPublisher`, en dernière étape.
  Pas depuis l'agrégat, pas depuis un adapter, pas depuis un contrôleur.
- Jamais d'`ApplicationEventPublisher` de Spring pour un fait métier : les deux familles
  d'événements restent séparées.
- Un nouvel événement se **déclare** dans le `DomainEventRegistration` de son contexte
  (`<contexte>/infrastructure/messaging/`), sinon le convertisseur ne le connaît pas et la
  désérialisation le refuse — c'est voulu, un `Class.forName` sur un nom venu du réseau
  n'est pas une option.
- Un listener est un **adapter entrant** dans `<contexte>/infrastructure/messaging/` :
  `@Profile("worker")`, une classe, une queue, une commande dispatchée sur le bus. Aucune
  règle métier, aucun accès direct à un repository.
- Un test qui observe une publication observe un **commit** : pas de `@Transactional`
  sur la classe, nettoyage explicite en `@AfterEach`. Un test du rôle worker pose
  `@ActiveProfiles("worker")` **et** `webEnvironment = NONE`.
```

- [ ] **Step 6: `README.md`**

« ## Stack » : ajouter RabbitMQ 4 (Spring AMQP) au back et « un processus worker, même
image, profil `worker` » à la ligne de l'application.

Tableau des URL (autour de la ligne 43), ajouter :
`| Console RabbitMQ (messages en dev) | http://localhost:15672 — guest / guest |`, et
compléter la ligne 28 : « démarre PostgreSQL, Mailpit, RabbitMQ, l'app, le worker et le
front ».

« ## Build de production », tableau des variables : quatre lignes
`SPRING_RABBITMQ_HOST` / `PORT` / `USERNAME` / `PASSWORD` (« Broker des événements
métier », défauts `localhost:5672`, `guest`/`guest`), puis un paragraphe :

```markdown
Le worker se déploie **depuis la même image**, avec `SPRING_PROFILES_ACTIVE=worker` et les
mêmes variables que l'API (base, mail, stockage, secret JWT, RabbitMQ). Il n'expose aucun
port : ne pas lui donner de domaine. Sans lui, les documents déposés restent `PENDING`.
```

- [ ] **Step 7: Vérification complète et commit**

Run: `docker compose down; make check-back`
Expected: formatage vérifié, toute la suite verte.

```bash
git add CLAUDE.md .claude/rules/backend.md README.md docs/superpowers/specs/2026-08-25-evenements-metier-rabbitmq-design.md
git commit -m "docs: décrit les événements métier, le rôle worker et l'écart de publication"
```

---

## Auto-revue du plan

**Couverture de la spec** — décision 1 : Task 5 ; 2 et 3 : Task 4 ; 4 : Task 3 ; 5 : Tasks 3
et 6 ; 6 et 7 : Task 6 et `application.yml` (Task 1) ; 8 : Task 6 ; 9 et 10 : Task 7 ; tests
de la spec : `DomainEventNamesTest` (Task 3), `AmqpDomainEventPublisherTest` (Task 4),
`UploadDocumentAnnouncementTest` (Task 5, c'est le « dispatche `UploadDocument` par le
bus » de la spec), `DocumentUploadedListenerTest` (Task 6) ; documentation : Task 8.

**Écarts par rapport à la spec, reportés dedans** — `JwtConfiguration` reste dans les deux
rôles (`JwtAccessTokenIssuer` en dépend) : seule `SecurityConfig` porte `@Profile("!worker")`.
`ExtractionQueueConfiguration` s'appelle `KnowledgeMessagingConfiguration`, parce qu'elle
déclare aussi le catalogue d'événements du contexte. Le test de publication après commit
passe par `TransactionTemplate` plutôt que par le bus (Task 4) ; le chemin du bus est
couvert par Task 5.

**Types** — `DomainEventNames.of(Class<? extends DomainEvent>)` et
`mappingOf(List<Class<? extends DomainEvent>>)` sont utilisés avec ces signatures dans
Tasks 3, 4 et 6 ; `KnowledgeMessagingConfiguration.EXTRACTION_QUEUE` est défini en Task 6
avant d'être lu par le listener ; `AmqpConfiguration.EVENTS_EXCHANGE` est défini en Task 3
et lu en Tasks 4, 5, 6.
