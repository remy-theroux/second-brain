# Événements métier sur RabbitMQ et rôle worker — design

Date : 2026-08-25 · Contexte : `shared` (socle) + `knowledge` (premier événement) ·
Branche : `feat/domain-events`

Ticket Notion : « RAG-4 — Extraire le texte et les sections d'un document »
(<https://app.notion.com/p/3c0215c5e46e815bac9dcf53bb7b73b9>), premier des deux
livrables de ce ticket. Le second, l'extraction elle-même, a sa propre spec et son propre
plan ; il se branche sur celui-ci par une seule ligne.

## Contexte

Un document déposé reste `PENDING` pour toujours : rien ne se déclenche après
`POST /api/documents`. RAG-4 veut en extraire le texte, RAG-5 le découper, RAG-6
enchaîner le tout. Chacune de ces étapes est un traitement long — quelques secondes pour
un PDF de 20 Mo — qui n'a rien à faire dans la requête HTTP qui l'a provoqué, ni dans le
processus qui sert l'API.

Le projet n'a aujourd'hui aucune notion d'événement métier, aucun traitement asynchrone,
un seul processus. `SpringCommandBus.dispatch` est synchrone et transactionnel ; tout ce
qu'un handler déclenche s'exécute dans la requête.

Arbitrages posés avec le porteur du ticket avant ce design, dans l'ordre où ils ont été
pris :

- Le dépôt ne lance pas l'extraction : il **publie un événement métier**, `DocumentUploaded`,
  et c'est une commande à part, `ExtractDocumentText`, qui extrait — dispatchée par un
  consommateur de cet événement. Les événements métier sont **séparés des événements
  Spring** techniques.
- Le consommateur est un **worker à part**, un second processus, pas un pool de threads dans
  l'API — un PDF lourd ne doit pas manger les threads qui servent les écrans.
- Le transport est **RabbitMQ**. Un transport PostgreSQL (Spring Integration JDBC,
  `LISTEN/NOTIFY`) a été étudié et écarté : le consommateur `@RabbitListener` est plus simple,
  et le broker apporte le push inter-processus sans connexion `LISTEN` à tenir.
- **On fait confiance au broker.** Pas d'outbox, pas de balayage de rattrapage : un
  événement que RabbitMQ n'a pas reçu est perdu. Décision explicite contre le
  sur-engineering, voir décision 3.

## Objectif

Un handler publie un événement métier depuis sa transaction ; il n'est émis que si la
transaction est commitée ; un processus worker, distinct de l'API, le reçoit et dispatche
une commande sur le bus. Le tout vérifiable en intégration, et exercé sur la pile de
développement.

**Réussi si :** `POST /api/documents` produit un message `knowledge.DocumentUploaded` sur
RabbitMQ portant l'identifiant du document ; un dépôt annulé n'en produit aucun ; le
conteneur `worker` de `docker compose` le reçoit et le journalise ; la suite de tests le
prouve sans rien lancer à la main.

## Attendus métier

Le ticket RAG-4 n'a pas de scénario pour cette partie : elle est la plomberie de son
premier scénario. Les scénarios ci-dessous sont ceux du socle, formulés au niveau où ils
sont observables.

```gherkin
Fonctionnalité: Événements métier

  Scénario: un dépôt réussi est annoncé
    Étant donné un compte connecté
    Quand il dépose un document
    Alors un événement « document déposé » portant l'identifiant du document est publié

  Scénario: un dépôt annulé n'est pas annoncé
    Étant donné une commande dont le traitement publie un événement puis échoue
    Quand elle est dispatchée
    Alors aucun événement n'est publié

  Scénario: le worker reçoit l'événement
    Étant donné un processus lancé dans le rôle worker
    Quand un événement « document déposé » est publié
    Alors le worker le reçoit et dispatche la commande correspondante
```

## Décisions de conception

Chaque décision a été arbitrée avant l'écriture du plan ; l'alternative écartée est
notée pour que le choix ne soit pas rejoué sans raison.

### 1. Le handler publie l'événement, explicitement

`UploadDocumentHandler` appelle `domainEventPublisher.publish(new DocumentUploaded(...))`
comme dernière étape de son orchestration, après `documentStorage.store(...)`.

Alternative écartée : l'agrégat collecte ses événements (`Document.upload` enregistre un
`DocumentUploaded` dans une liste sur l'entité) et l'adapter JPA les publie au `save`.
C'est plus pur — l'événement naît là où l'état change — mais ça coûte une liste transiente
sur l'entité, un vidage dans l'adapter et une règle de plus à expliquer, pour un seul
événement. Le handler orchestre déjà le dépôt ; publier est une étape de cette
orchestration, et elle se lit dans le handler. Si un jour trois handlers publient depuis
le même agrégat, la collecte par l'agrégat redeviendra la bonne réponse.

### 2. Publication après commit, jamais dans la transaction

L'adapter n'envoie pas au moment de `publish`. Si une transaction est active — toujours
le cas depuis `SpringCommandBus` — il enregistre une `TransactionSynchronization` et
envoie dans `afterCommit`. Sans transaction active, il envoie immédiatement.

Un rollback n'émet donc rien : pas d'événement fantôme désignant une ligne qui n'existe
pas. C'est la garantie qui compte pour un consommateur qui va relire le document en base.

### 3. On fait confiance au broker : pas d'outbox, pas de rattrapage

`afterCommit` s'exécute **après** que la base a commité. Si RabbitMQ est injoignable à cet
instant, le dépôt est acquis, l'événement est perdu, et le document reste `PENDING` sans
que rien ne le reprenne.

Deux parades ont été étudiées et écartées, à la demande du porteur du ticket : l'outbox
en base avec relais (une table, un relais, ses tests — c'est ce que le choix de RabbitMQ
venait d'éviter) et le balayage périodique des documents `PENDING` (un `@Scheduled` qui
republie). **Écart assumé**, à inscrire dans `CLAUDE.md`. Condition de bascule : un
événement qui n'aurait pas d'état observable derrière lui — on ne peut pas balayer ce
qu'on ne voit pas —, ou une perte constatée en production.

Ce que ça permet : `DomainEventPublisher` reste un port. L'outbox, le jour venu, est une
implémentation de plus derrière lui, et aucun handler ne change.

### 4. Le domaine ne connaît ni AMQP ni le nom de la file

`DomainEvent` est une interface marqueur dans `shared/event/`, sans import Spring, avec un
seul contrat : `Instant occurredAt()`. Le port `DomainEventPublisher` y vit aussi. Les
événements sont des records dans `<contexte>/domain/event/`.

La **clé de routage est dérivée par l'adapter** de la classe de l'événement : le premier
segment du package sous `xyz.sterenn.secondbrain` plus le nom simple de la classe —
`knowledge.DocumentUploaded`. Le domaine ne nomme rien. Cette même chaîne sert d'en-tête
de type sur le message, **jamais le FQCN** : renommer un package ne casse pas les messages
en vol, et le nom se lit dans la console du broker.

Alternative écartée : une méthode `String name()` sur `DomainEvent`. Elle mettrait une
convention de transport dans le domaine, et deux événements pourraient se donner le même
nom sans que rien ne le voie.

### 5. Un exchange topic, une queue par consommateur

Un seul exchange *topic*, `second-brain.events`. Chaque consommateur déclare **sa** queue
et son binding — `knowledge.extraction` liée à `knowledge.DocumentUploaded`. Les
déclarations sont idempotentes ; les deux rôles les font au démarrage, donc l'ordre de
démarrage est indifférent.

Un topic plutôt qu'un direct pour que RAG-6 puisse un jour écouter `knowledge.#` sans
rien redéclarer côté publication. Pas de queue partagée entre contextes : une queue est
une intention de consommation, elle appartient à celui qui consomme.

### 6. Un listener par événement, dans l'infrastructure du contexte qui consomme

`DocumentUploadedListener` vit dans `knowledge/infrastructure/messaging/` : c'est un
adapter entrant, au même titre qu'un contrôleur, et la règle « une classe, un mapping »
des contrôleurs vaut pour lui — un listener, une queue, une commande dispatchée. Il
désérialise, dispatche sur le `CommandBus`, rien d'autre.

Dans ce livrable, aucune commande n'existe encore à dispatcher : le listener journalise
l'événement reçu en `INFO`. Le plan d'extraction remplacera cette ligne par
`commandBus.dispatch(new ExtractDocumentText(event.documentId()))`.

### 7. Une exception dans le listener rejette sans remise en file

`spring.rabbitmq.listener.simple.default-requeue-rejected=false`. Sans ce réglage, un
message dont le traitement lève une exception est remis en file et retraité
indéfiniment : un PDF qui fait planter l'extracteur bloquerait le worker pour toujours.

Pas de dead-letter queue, pas de retry : une extraction qui échoue doit finir en
`FAILED` sur le document, avec sa raison, pas être rejouée. Le message rejeté est
journalisé en `ERROR` avec son type et son corps ; c'est tout ce qu'on en garde. La DLQ
est trois lignes de déclaration le jour où on voudra inspecter les messages rejetés.

### 8. Le rôle est un profil Spring ; sans profil, le processus est l'API

Le profil `worker` change le rôle du processus. Sans lui, rien ne change pour un
déploiement existant : l'API reste l'API, Coolify n'a rien à modifier tant que le worker
n'y est pas déployé.

Ce que le profil `worker` fait : `spring.main.web-application-type=none` dans
`application-worker.yml` — pas de Tomcat, donc aucun contrôleur mappé, ni Swagger, ni
actuator HTTP. `SecurityConfig` porte `@Profile("!worker")` : elle réclame
`HttpSecurity`, qui n'existe pas sans servlet. `JwtConfiguration`, elle, reste dans les
deux rôles — `JwtAccessTokenIssuer` en dépend et n'a rien de propre au web. Les
contrôleurs restent des beans inoffensifs sans mapping ; ils n'ont pas besoin d'annotation.

Ce que le profil `worker` active : les listeners, annotés `@Profile("worker")`. Dans le
rôle API, ils n'existent pas — l'API publie, elle ne consomme jamais.

Ce qui est commun : bus, JPA, Flyway, stockage, mail, et le `DomainEventPublisher` — le
worker publiera à son tour (`DocumentExtracted`, un jour). Flyway tourne dans les deux
rôles : le worker démarré avant l'API migre lui-même, et Flyway verrouille la table
d'historique, donc deux démarrages simultanés ne se marchent pas dessus.

Alternative écartée : deux `main`, deux modules Gradle. Une seule image, un seul jar, un
seul build : ce qui distingue les rôles tient en un fichier de profil et deux annotations.

### 9. En développement, le worker est un second conteneur de la même image

`compose.yaml` gagne deux services : `rabbitmq` et `worker`. Le worker utilise
`Dockerfile.dev`, `SPRING_PROFILES_ACTIVE=dev,worker`, les mêmes volumes que `app` —
**mais pas de compilateur continu** : il s'appuie sur le `-t classes` du conteneur `app`,
et DevTools redémarre le worker au même `.class` modifié. Il lui faut **deux** isolations
Gradle, pas une : un `--project-cache-dir` propre (`.gradle-worker/`, ignoré par git) pour
le `.gradle/` du projet, et un `GRADLE_USER_HOME` propre (`.gradle-cache-worker/`, volume
nommé `gradle-cache-worker`) — deux conteneurs qui partagent un `GRADLE_USER_HOME` se
bloquent sur ses verrous, la contention Gradle passant par un port local invisible de
l'autre conteneur. Prix : un second téléchargement des dépendances au premier démarrage de
la pile.

**Validé sur la pile** (plan, tâche 7) : la première tentative, avec le seul
`--project-cache-dir`, a échoué sur « Timeout waiting to lock journal cache » ; la seconde
isolation l'a réglé, et le repli (un seul conteneur portant `dev,worker`) n'a pas été
nécessaire.

La revue de branche a resserré l'ordonnancement : le worker ne compile plus du tout
(`bootRun -x compileJava -x processResources`, donc `build/` n'a qu'un seul écrivain,
`app`), et il attend que `app` soit **sain** — healthcheck TCP sur son port 8080 — et non
plus simplement démarré, ce qui garantit `build/classes` peuplé avant son premier `bootRun`.

### 10. La console RabbitMQ a son port, comme Mailpit

`rabbitmq:4-management-alpine`, AMQP sur `${RABBITMQ_PORT:-5672}`, console sur
`${RABBITMQ_WEB_PORT:-15672}`. Elle n'est pas routée par Traefik : c'est un outil de
développement, pas une partie de l'application — même statut que Mailpit sur 8025. Le
skill `worktree` apprend deux ports de plus dans son bloc décalé.

## Architecture

```
xyz.sterenn.secondbrain
├── config/
│   ├── SecurityConfig            @Profile("!worker")
│   ├── JwtConfiguration          commune aux deux rôles (JwtAccessTokenIssuer en dépend)
│   └── ...
├── shared/
│   ├── bus/                      inchangé
│   └── event/
│       ├── DomainEvent           interface : Instant occurredAt()
│       ├── DomainEventPublisher  port : void publish(DomainEvent)
│       └── amqp/
│           ├── AmqpDomainEventPublisher     adapter : afterCommit → RabbitTemplate
│           ├── DomainEventNames             clé de routage / en-tête de type ↔ classe
│           └── AmqpConfiguration            exchange, convertisseur JSON, ObjectMapper
└── knowledge/
    ├── domain/event/
    │   └── DocumentUploaded      record(UUID documentId, UUID ownerId, Instant occurredAt)
    ├── application/command/
    │   └── UploadDocumentHandler publie DocumentUploaded en dernière étape
    └── infrastructure/messaging/
        ├── KnowledgeMessagingConfiguration  catalogue des événements du contexte,
        │                                    queue knowledge.extraction + binding
        └── DocumentUploadedListener         @RabbitListener @Profile("worker")

src/main/resources/
├── application.yml               spring.rabbitmq.* (hôte, port, identifiants par variables),
│                                 default-requeue-rejected=false
└── application-worker.yml        spring.main.web-application-type=none

compose.yaml                      + rabbitmq, + worker
.env.example                      + RABBITMQ_PORT, RABBITMQ_WEB_PORT
gradle/libs.versions.toml         starter amqp et testcontainers-rabbitmq, sans version (BOM)
.claude/skills/worktree/          + deux ports dans le bloc décalé
```

### Domaine (`shared/event`, `knowledge/domain/event`)

`DomainEvent` et `DomainEventPublisher` sont dans `shared/event/` pour la même raison que
les bus sont dans `shared/bus/` : aucun contexte ne doit dépendre d'un autre pour
publier. Le sous-package `amqp/` est l'infrastructure de ce socle ; le domaine de `shared`
ne l'importe pas, et la règle « le domaine n'importe jamais `org.springframework.*` »
vaut pour `DomainEvent` et le port.

`DocumentUploaded` porte `documentId` — ce que le consommateur relira —, `ownerId` — pour
que RAG-6 puisse un jour router ou journaliser par compte sans relire — et `occurredAt`,
fourni par le `Clock` de `ClockConfiguration`. Rien d'autre : ni nom de fichier, ni format,
ni empreinte. Le consommateur relit le document ; l'événement dit *qu'il* s'est passé
quelque chose, pas *quoi* en détail.

### Application

`UploadDocumentHandler` reçoit un `DomainEventPublisher` de plus par son constructeur et
publie après le stockage. Le Javadoc du handler, qui explique déjà l'ordre des étapes,
gagne une phrase : la publication est en dernier parce qu'elle ne prend effet qu'au
commit, donc sa place dans la séquence n'a aucune importance transactionnelle — elle est
dernière pour se lire comme ce qu'elle est, une annonce.

### Infrastructure

`AmqpDomainEventPublisher` — `@Component`, implémente le port. `publish` :
si `TransactionSynchronizationManager.isSynchronizationActive()`, enregistre une
synchronisation dont `afterCommit` fait `rabbitTemplate.convertAndSend(exchange,
routingKey, event)` ; sinon envoie tout de suite. Une exception dans `afterCommit` est
journalisée en `ERROR` avec le type et l'identifiant — elle ne peut plus annuler le
commit, et elle ne doit pas faire échouer la requête dont l'écriture est acquise.

`DomainEventNames` — `static String of(Class<? extends DomainEvent>)` : segment de contexte
+ nom simple. C'est aussi lui qui construit le `DefaultClassMapper` du convertisseur JSON,
pour que l'en-tête de type porte ce nom et se résolve en classe côté consommateur. Les
classes d'événements connues sont **déclarées** (une liste par contexte, enregistrée dans
`AmqpConfiguration`), pas scannées : un événement absent de la liste échoue à la
désérialisation avec un message qui dit lequel, plutôt que par un `ClassNotFoundException`
sur un FQCN.

`AmqpConfiguration` — le `TopicExchange`, le `JacksonJsonMessageConverter` branché sur
l'`ObjectMapper` Jackson 3 de Boot (`tools.jackson`, `JavaTimeModule` inclus pour
`Instant`), et le `RabbitTemplate` qui l'utilise.

`KnowledgeMessagingConfiguration` — la déclaration des événements du contexte
(`DomainEventRegistration`, lue par `AmqpConfiguration`), la `Queue("knowledge.extraction",
durable)` et son binding sur `knowledge.DocumentUploaded`. Dans `knowledge`, parce que la
queue appartient au consommateur. Déclarée dans les deux rôles.

`DocumentUploadedListener` — `@Component @Profile("worker")`,
`@RabbitListener(queues = "knowledge.extraction")`, méthode
`on(DocumentUploaded event)`. Journalise en `INFO`, avec l'identifiant du document. La
ligne à remplacer par le plan d'extraction est marquée par un commentaire qui le dit.

### Configuration

`application.yml` : `spring.rabbitmq.host/port/username/password` par variables
`SPRING_RABBITMQ_*`, défauts `localhost`, `5672`, `guest`, `guest` — le même statut que la
datasource : le défaut sert le développement hors conteneur, `compose.yaml` pose les vrais.
`spring.rabbitmq.listener.simple.default-requeue-rejected: false`, commenté (décision 7).

`application-worker.yml` : `spring.main.web-application-type: none`, et rien d'autre.

`compose.yaml` : `rabbitmq` avec healthcheck (`rabbitmq-diagnostics -q ping`) ; `app` et
`worker` en `depends_on` `service_healthy` ; `SPRING_RABBITMQ_HOST=rabbitmq` sur les deux.
Le worker n'a pas de labels Traefik : il n'écoute rien.

## Tests

`TestcontainersConfiguration` gagne un `RabbitMQContainer` avec `@ServiceConnection`
(`rabbitmq:4-management-alpine`) à côté de PostgreSQL. Toute la suite l'aura, démarré une
fois : Spring AMQP ne se connecte qu'au premier envoi, mais les tests du socle en ont
besoin, et un conteneur de plus partagé coûte moins qu'une configuration de test à part.

Les tests portent sur le **port** `DomainEventPublisher` et sur le contrat du message,
pas sur `RabbitTemplate` :

- `DomainEventNamesTest` — unitaire, sans Spring : `knowledge.DocumentUploaded` pour la
  classe, et un événement d'un package hors convention lève une `IllegalArgumentException`
  qui nomme la classe.
- `AmqpDomainEventPublisherTest` — `@SpringBootTest`, **non** `@Transactional` puisqu'il
  observe un commit, nettoyage en `@AfterEach` comme `CommandBusTransactionTest` :
  - `publie_l_evenement_apres_le_commit` : dispatche `UploadDocument` par le bus, puis lit
    la queue `knowledge.extraction` (`rabbitTemplate.receiveAndConvert(queue, timeout)`) et
    vérifie l'identifiant du document et l'en-tête de type. Purge la queue avant, efface le
    document et son fichier après.
  - `ne_publie_rien_quand_la_transaction_est_annulee` : une commande de test dont le handler
    publie puis lève ; après le refus, la queue reste vide sur le délai d'attente.
  - `publie_immediatement_hors_transaction` : `publish` appelé hors bus ; le message
    arrive.
- `DocumentUploadedListenerTest` — `@SpringBootTest(webEnvironment = NONE)` +
  `@ActiveProfiles("worker")` : un contexte **sans Tomcat**, ce qui vérifie du même coup
  que le rôle démarre (donc que `SecurityConfig` s'efface bien). Envoie un message sur la queue
  et vérifie la journalisation avec `OutputCaptureExtension`. Le plan d'extraction
  remplacera l'assertion sur le journal par une assertion sur le statut du document.
- `SecondBrainApplicationTests` reste tel quel : le rôle API par défaut démarre avec
  RabbitMQ dans le contexte.

Ce qui n'est pas testé automatiquement, et se vérifie à la main sur la pile compose :
que le conteneur `worker` reçoit bien l'événement d'un dépôt fait dans le navigateur
(un `docker compose logs -f worker` pendant un dépôt), et le hot reload du worker
(décision 9).

## Hors-périmètre

- L'extraction elle-même, `ExtractDocumentText`, les statuts `EXTRACTING/EXTRACTED/FAILED`,
  la table des blocs, la route et l'écran de consultation : spec et plan séparés.
- Outbox, balayage de rattrapage, dead-letter queue, retry : décisions 3 et 7.
- Le déploiement Coolify du service `rabbitmq` et du worker : hors dépôt, comme le
  routage — l'écart n° 12 de `CLAUDE.md` s'étend à ces deux services. Le README dit ce
  qu'il faut poser (`SPRING_PROFILES_ACTIVE=worker`, `SPRING_RABBITMQ_*`).
- Un health check du worker autre que « le processus tourne ».
- Plusieurs instances du worker : rien ne l'empêche (une queue, plusieurs consommateurs),
  rien ne le teste.

## Écarts d'implémentation

Ce que le code fait autrement que cette spec, et pourquoi. Rien qui change une décision ;
tout tenait à un détail que l'écriture a fait apparaître.

- **Image de test `rabbitmq:4-alpine`**, pas `4-management-alpine` : la console ne sert à
  rien dans un conteneur jetable, et l'image sans elle démarre plus vite. `compose.yaml`
  garde bien la variante management (décision 10).
- **Le convertisseur construit son propre mapper Jackson** (`new JacksonJsonMessageConverter()`,
  constructeur sans argument) plutôt que de recevoir l'`ObjectMapper` de Boot. Délibéré :
  une personnalisation de la sérialisation HTTP ne doit pas changer la forme des messages en
  vol. Jackson 3 y apporte le module `java.time`, donc les `Instant` partent en ISO-8601.
- **`DefaultJacksonJavaTypeMapper` et non `DefaultClassMapper`**, avec
  `TypePrecedence.TYPE_ID` : c'est l'en-tête `__TypeId__` qui gouverne dans les deux sens.
  En `INFERRED` — le défaut — la réception déduirait le type du paramètre du listener et
  n'ouvrirait jamais l'en-tête, ce qui viderait la table des noms de son sens à la réception.
- **Le test de publication de la spec s'est scindé en deux** : `AmqpDomainEventPublisherTest`
  vérifie le contrat du *port* — l'annonce part au commit, jamais avant, jamais après un
  rollback — sur une queue d'observation liée à `knowledge.#` ; `UploadDocumentAnnouncementTest`
  vérifie le chemin réel, `UploadDocument` dispatché sur le bus. Un seul test aurait mélangé
  « l'annonce part au commit » et « le handler publie ».
- **Queues d'observation durables** dans les tests : RabbitMQ 4 refuse par défaut une queue
  transiente non exclusive (`transient_nonexcl_queues`, dépréciée), et une queue exclusive ne
  se lirait que depuis la connexion qui l'a déclarée.
- **Le nom de l'événement est dérivé dans `publish`**, avant l'enregistrement de la
  synchronisation : un événement hors contexte borné est une erreur de programmation, elle
  doit faire échouer la commande avant le commit, pas remonter après.
- **Deux isolations Gradle pour le worker**, pas une (décision 9) : `--project-cache-dir`
  *et* un `GRADLE_USER_HOME` propre.

## Pointeurs

- `docs/superpowers/plans/2026-08-25-evenements-metier-rabbitmq.md` — le plan.
- Spec de l'extraction, à venir : `docs/superpowers/specs/2026-08-25-extraction-texte-design.md`.
- `CLAUDE.md`, « La transaction vit dans le bus » — pourquoi la publication ne peut pas se
  faire dans la transaction.
- Spring AMQP, publication conditionnelle à la transaction : `RabbitTemplate` sait le faire
  avec `setChannelTransacted(true)`, mais ça synchronise un canal AMQP transactionnel avec
  la transaction JDBC (best effort, pas 2PC) — plus lourd que `afterCommit` pour la même
  garantie.
