# Connecter un compte Google Drive — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Obtenir et **conserver durablement** une autorisation de lecture sur un Google Drive.
Ce ticket ne lit aucun fichier : il ne fait qu'obtenir le droit de le faire, et c'est le socle
des six tickets DRIVE qui suivent.

**Architecture:** Le flux « code d'autorisation » d'OAuth 2.0, en deux routes et deux entités.

`POST /api/drive/authorizations` (authentifiée) ouvre une **demande d'autorisation** : un nonce
aléatoire est persisté avec son propriétaire, et l'URL de consentement Google est rendue au
front, qui y envoie le navigateur. `GET /drive/callback` — **hors `/api`**, parce que le lien
vient de Google et n'a aucun jeton en en-tête — retrouve la demande par son `state`, la consomme,
échange le code contre un jeton de rafraîchissement, lit l'adresse Google du compte, remplace
la connexion précédente s'il y en avait une, et **redirige vers `/documents?drive=<code>`**.

Quatre choix de conception, dont trois suivent un précédent du dépôt :

- **Le jeton de rafraîchissement est chiffré au repos par un `AttributeConverter`.** C'est
  exactement le chemin d'`Email` et de `Checksum` : un value object `RefreshToken` dans le
  domaine, un `RefreshTokenAttributeConverter` en `@Converter(autoApply = true)` dans
  `infrastructure/persistence/`, et **aucune classe du domaine ne nomme le chiffrement**. La clé
  (`secondbrain.drive.token-encryption-key`) n'a **aucun défaut**, comme `jwt.secret` : sans
  elle, l'application refuse de démarrer. AES-256-GCM, un IV aléatoire de 12 octets préfixé au
  chiffré, le tout en Base64.
- **Le `state` est persisté en clair, à usage unique, et il expire.** Ce n'est pas un secret
  comme le jeton de vérification — le connaître ne donne rien — c'est un nonce anti-CSRF, et le
  ticket dit qu'il est la seule protection du flux (le projet n'a ni session ni CSRF, ADR-0003).
  Il suit donc le cycle de vie de `VerificationToken` (émis, expirant, consommé une fois) sans
  son empreinte salée.
- **L'adresse Google vient de `about.get?fields=user`**, pas d'un scope supplémentaire :
  `drive.readonly` y donne déjà accès, et demander `userinfo.email` élargirait le consentement
  pour rien.
- **Aucun SDK Google.** Un adapter écrit à la main sur le `RestClient` de Spring, comme
  `OllamaEmbeddingAdapter` : le point de jeton est un POST de formulaire et `about.get` un GET
  JSON. `google-api-client` amènerait Guava, protobuf et sa propre couche HTTP pour deux appels.
  **C'est le seul choix de ce plan qui ferme une alternative crédible, et il appellera un ADR** —
  il n'est pas écrit ici, les règles du dépôt l'interdisant sans accord préalable.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 (MVC, Security) · Flyway · PostgreSQL ·
JUnit 5 + AssertJ + Testcontainers. **Aucune dépendance nouvelle** : `RestClient` vient de
`spring-web`, `javax.crypto` du JDK.

**Ticket:** DRIVE-1 — Connecter un compte Google Drive.

## Global Constraints

- **Branche :** `feat/drive-1-connexion-google`, dans `/home/remy-theroux/projects/second-brain`.
- **Aucun JDK ni Gradle sur l'hôte.** Définir cette fonction **une fois** :

  ```bash
  gtest() {
    docker run --rm --network host \
      -v "$PWD":/app -w /app \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v second-brain-gradle-home:/home/gradle/.gradle \
      gradle:jdk25 gradle --no-daemon "$@"
  }
  ```

- **`gtest` et `docker compose up` ne cohabitent pas.** `docker compose down` avant tout `gtest`.
- **Jamais de `@Transactional` sur un handler** : la transaction appartient au bus.
- **Toute exception métier hérite de `RuntimeException`**, message affichable tel quel, en
  français.
- **Aucun secret ne fuit** : `RefreshToken.toString()` est masqué, la commande qui le transporte
  redéfinit `toString()`, aucune route ne le rend, aucun journal ne l'écrit.
- **Une route publique sous `/api` doit se déclarer dans `SecurityConfig`.** Ici, aucune :
  `/drive/callback` vit **hors** de `/api`, donc `anyRequest().permitAll()` la couvre déjà — ne
  rien ajouter à `SecurityConfig`.
- **Mais vivre hors de `/api` a un prix, et c'est le piège de ce ticket :** la règle Traefik de
  `compose.yaml` énumère les préfixes qui vont à l'application Java
  (`/api`, `/verification`, `/swagger-ui`, `/v3/api-docs`, `/actuator`). **`/drive` n'y est
  pas**, donc sans ajout le callback de Google atterrit sur le front, qui répond son `index.html`
  et la connexion échoue en silence. Ajouter `|| PathPrefix(\`/drive\`)` à
  `traefik.http.routers.backend.rule`. En production, c'est Coolify qui tient ce rôle, hors du
  dépôt (ADR-0013) : le dire dans la documentation.
- **La clé de chiffrement doit exister dans les trois environnements de test.**
  `build.gradle.kts` pose `SECONDBRAIN_JWT_SECRET` sur la tâche `Test` (ligne ~144) **et**
  `src/test/resources/application.properties` le redit pour les lancements hors Gradle. La clé
  Drive suit exactement le même double chemin, sans quoi la moitié des tests démarre et l'autre
  non.
- **Langue :** code, commentaires, Javadoc, noms de méthodes de test en **anglais** ; messages
  d'exception métier et messages de commit en **français**.
- **Commentaires :** l'exception, jamais l'habitude. Trois lignes est un plafond.
- **Formatage :** `make format-back` avant tout commit.
- **Aucun ADR n'est écrit dans ce plan** — voir le quatrième point de conception ci-dessus.

## Fichiers

**Créés — domaine**

| Fichier | Responsabilité |
|---|---|
| `…/knowledge/domain/valueobject/RefreshToken.java` | Le jeton en clair, `toString()` masqué |
| `…/knowledge/domain/valueobject/DriveAuthorizationState.java` | Le nonce, `random()` en Base64url |
| `…/knowledge/domain/valueobject/DriveConnectionStatus.java` | `ACTIVE` / `NEEDS_RECONNECTION` |
| `…/knowledge/domain/valueobject/DriveGrant.java` | Ce que Google rend : adresse et jeton de rafraîchissement |
| `…/knowledge/domain/entity/DriveConnection.java` | Une connexion par compte, son cycle de vie |
| `…/knowledge/domain/entity/DriveAuthorizationRequest.java` | La demande en cours : nonce, expiration, usage unique |
| `…/knowledge/domain/DriveAuthorizationPolicy.java` | La durée de validité d'une demande (10 min) |
| `…/knowledge/domain/port/DriveConnectionRepository.java` | |
| `…/knowledge/domain/port/DriveAuthorizationRequestRepository.java` | |
| `…/knowledge/domain/port/GoogleDriveAuthorization.java` | L'URL de consentement, l'échange du code |
| `…/knowledge/domain/exception/InvalidDriveAuthorizationException.java` | Le retour falsifié, périmé ou déjà utilisé |
| `…/knowledge/domain/exception/DriveConsentDeniedException.java` | Le refus chez Google |
| `…/knowledge/domain/exception/DriveConnectionNotFoundException.java` | |
| `…/knowledge/domain/exception/GoogleDriveUnavailableException.java` | Google injoignable ou en erreur |

**Créés — application**

| Fichier | Responsabilité |
|---|---|
| `…/application/command/StartDriveAuthorization.java` + handler | Persiste la demande |
| `…/application/command/CompleteDriveAuthorization.java` + handler | Échange, remplace, active |
| `…/application/command/DisconnectDrive.java` + handler | Révoque la connexion |
| `…/application/query/FindDriveConnection.java` + handler + `DriveConnectionView.java` | L'état, sans le jeton |

**Créés — infrastructure**

| Fichier | Responsabilité |
|---|---|
| `…/infrastructure/persistence/RefreshTokenAttributeConverter.java` | AES-256-GCM, `autoApply` |
| `…/infrastructure/persistence/Jpa*RepositoryAdapter.java` + `SpringData*Repository.java` | Les deux repositories |
| `…/infrastructure/drive/GoogleDriveAuthorizationAdapter.java` | Le seul endroit qui parle à Google |
| `…/infrastructure/drive/GoogleDriveConfiguration.java` | Le `RestClient` et les propriétés |
| `…/infrastructure/drive/GoogleTokenResponse.java`, `GoogleAboutResponse.java` | Les corps JSON |
| `…/infrastructure/web/StartDriveAuthorizationController.java` | `POST /api/drive/authorizations` |
| `…/infrastructure/web/CompleteDriveAuthorizationController.java` | `GET /drive/callback` |
| `…/infrastructure/web/DisconnectDriveController.java` | `DELETE /api/drive/connection` |
| `…/infrastructure/web/ShowDriveConnectionController.java` | `GET /api/drive/connection` |
| `…/infrastructure/web/DriveAuthorizationResponse.java` | `{ authorizationUrl }` |
| `src/main/resources/db/migration/V14__create_knowledge_drive_connections.sql` | Les deux tables |

**Créés — tests**

| Fichier | Responsabilité |
|---|---|
| `src/test/…/knowledge/FakeGoogleDriveAuthorizationConfiguration.java` | Un Google bouchonné, sur le modèle de `RecordingNotificationSenderConfiguration` |
| `src/test/…/knowledge/domain/entity/DriveAuthorizationRequestTest.java` | Expiration et usage unique, sans Spring |
| `src/test/…/knowledge/infrastructure/persistence/RefreshTokenAttributeConverterTest.java` | Le chiffré en base n'est pas le clair |
| `src/test/…/knowledge/infrastructure/web/DriveConnectionFlowTest.java` | Les six scénarios du ticket |

**Modifiés**

| Fichier | Modification |
|---|---|
| `src/main/resources/application.yml` | Le bloc `secondbrain.drive` |
| `src/test/resources/application.properties` | Des valeurs de test pour ce bloc |
| `compose.yaml` / `.env.example` | Les variables de développement |
| `CLAUDE.md` | Le récit du flux, les tables, la stack |

---

### Task 1: Le jeton de rafraîchissement, chiffré au repos

**Files:**
- Create: `…/knowledge/domain/valueobject/RefreshToken.java`
- Create: `…/knowledge/infrastructure/persistence/RefreshTokenAttributeConverter.java`
- Create: `src/test/…/knowledge/infrastructure/persistence/RefreshTokenCipherTest.java`
- Modify: `src/main/resources/application.yml`, `src/test/resources/application.properties`

**Interfaces:**
- Consomme : rien.
- Produit : `RefreshToken`, projeté sur une colonne `text` chiffrée. Consommé par la tâche 2.

- [ ] **Step 1: Écrire les tests qui échouent**

`RefreshTokenCipherTest` est un test **unitaire pur** sur le converter, instancié à la main avec
une clé de test — pas un `@SpringBootTest`. Il vérifie quatre choses :

| Test | Ce qu'il vérifie |
|---|---|
| `restores_the_token_it_enciphered` | Aller-retour : `convertToEntityAttribute(convertToDatabaseColumn(t))` rend `t` |
| `never_writes_the_clear_token_to_the_column` | La colonne ne **contient pas** la sous-chaîne du jeton clair |
| `enciphers_the_same_token_differently_twice` | Deux chiffrés diffèrent — l'IV est aléatoire, pas fixe |
| `refuses_a_ciphertext_that_was_tampered_with` | Un octet retourné dans le Base64 fait lever, GCM étant authentifié |

Plus un test sur le value object lui-même : `hides_its_value_in_toString`.

- [ ] **Step 2: Lancer les tests, les voir échouer**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.persistence.RefreshTokenCipherTest"
```

- [ ] **Step 3: Écrire le value object**

`RefreshToken` est un `record RefreshToken(String value)` du domaine : constructeur compact
refusant le vide, `toString()` masqué comme celui de `RawVerificationToken`. Il ne connaît **ni**
le chiffrement **ni** JPA.

- [ ] **Step 4: Écrire le converter**

`@Converter(autoApply = true)`, dans `infrastructure/persistence/`, **sans `@Component`** —
Hibernate l'instancie lui-même, donc la clé ne peut pas être injectée par constructeur Spring.
Elle est lue une fois, statiquement, depuis la propriété système alimentée au démarrage… **non** :
c'est le piège de ce ticket. Deux options, et c'est la seconde qu'on retient :

1. lire `System.getenv` dans le converter — intestable et hors configuration Spring ;
2. **faire du converter un bean Spring quand même.** Hibernate 6 sait résoudre un converter par
   le `BeanContainer` de Spring : `spring-orm` installe `SpringBeanContainer` par défaut dans
   `LocalContainerEntityManagerFactoryBean`. Un converter annoté `@Component` **et**
   `@Converter(autoApply = true)` est donc instancié par Spring, avec injection de constructeur,
   et reste découvert par le scan d'entités. C'est un écart au commentaire de `backend.md`
   (« Pas de `@Component` : Hibernate instancie le converter lui-même ») ; **le dire dans la PR**,
   et poser un commentaire de trois lignes maximum à l'endroit qui l'exige.

Le chiffrement : AES-256-GCM, IV de 12 octets tiré par `SecureRandom` à chaque écriture, tag de
128 bits, sortie `Base64(IV || chiffré)`. La clé arrive en Base64 par
`secondbrain.drive.token-encryption-key`, **sans valeur par défaut** ; une clé qui ne fait pas
32 octets fait échouer le démarrage avec un message qui le dit.

- [ ] **Step 5: Configurer**

`application.yml` : le bloc `secondbrain.drive`, avec le même commentaire de statut que
`jwt.secret` — pas de défaut, une clé par défaut serait une clé publique.
`src/test/resources/application.properties` : une clé de test, comme le secret JWT y est déjà.

- [ ] **Step 6: Vérifier, formater, committer**

Message : `feat: le jeton de rafraîchissement Drive est chiffré au repos`

---

### Task 2: Les deux entités et leurs tables

**Files:**
- Create: les deux entités, `DriveConnectionStatus`, `DriveAuthorizationState`,
  `DriveAuthorizationPolicy`, les deux ports repository, les quatre exceptions
- Create: `V14__create_knowledge_drive_connections.sql`
- Create: `src/test/…/knowledge/domain/entity/DriveAuthorizationRequestTest.java`

**Interfaces:**
- Consomme : `RefreshToken`.
- Produit : `DriveConnection`, `DriveAuthorizationRequest` et leurs ports. Consommés par la
  tâche 4.

- [ ] **Step 1: Écrire les tests qui échouent**

`DriveAuthorizationRequestTest`, unitaire pur, sur le modèle de `VerificationTokenTest` :

| Test | Ce qu'il vérifie |
|---|---|
| `draws_a_state_that_differs_from_one_request_to_the_next` | Le nonce est aléatoire |
| `accepts_a_state_that_matches_before_it_expires` | Le cas nominal |
| `refuses_a_state_that_does_not_match` | `InvalidDriveAuthorizationException` |
| `refuses_a_request_older_than_the_policy` | Idem, à `VALIDITY` + une seconde |
| `refuses_a_request_already_consumed` | Idem — usage unique |

- [ ] **Step 2: Les voir échouer, puis écrire le domaine**

`DriveAuthorizationRequest.open(ownerId, clock.instant())` tire son `state` lui-même —
`DriveAuthorizationState.random()`, 32 octets en Base64url sans padding, comme
`RawVerificationToken`. `consume(state, now)` porte les trois règles et lève. `DriveConnection`
porte `connect(ownerId, googleEmail, refreshToken, now)`, `markNeedsReconnection()` et
`refresh(googleEmail, refreshToken, now)` — ce dernier sert au scénario « connexion alors qu'un
compte est déjà connecté », qui **remplace** au lieu de créer un second compte.

Les trois refus du flux (`state` illisible, demande inconnue, `state` faux) partagent **un seul
message** : les distinguer ferait de la route un oracle. Même raisonnement que les trois façons
de présenter un lien de vérification inexploitable.

- [ ] **Step 3: La migration**

```sql
CREATE TABLE knowledge_drive_connections (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL UNIQUE REFERENCES users_users (id) ON DELETE CASCADE,
    google_email varchar(320) NOT NULL,
    refresh_token text NOT NULL,
    status varchar(32) NOT NULL,
    connected_at timestamptz NOT NULL
);

CREATE TABLE knowledge_drive_authorization_requests (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users_users (id) ON DELETE CASCADE,
    state varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    consumed_at timestamptz
);
```

`owner_id UNIQUE` **est** la règle « une connexion par compte utilisateur », posée là où elle ne
peut pas être contournée. Vérifier la forme exacte des migrations existantes (`V5`, `V11`) avant
d'écrire celle-ci : type des colonnes d'horodatage, présence ou non d'une clé étrangère vers
`users_users`, et la reproduire plutôt que d'inventer.

- [ ] **Step 4: Les adapters de persistance**

Sur le modèle exact de `JpaDocumentRepositoryAdapter` / `SpringDataDocumentRepository` :
`SpringData*` est **package-private**, l'adapter traduit les erreurs techniques en erreurs
métier, et c'est le **port** que les tests injecteront.

- [ ] **Step 5: Vérifier, formater, committer**

`gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.*"` puis
`gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"` — c'est ce dernier qui
constate que Flyway et `ddl-auto: validate` s'accordent.

Message : `feat: une connexion Drive et sa demande d'autorisation entrent en base`

---

### Task 3: L'adapter Google

**Files:**
- Create: `…/knowledge/domain/port/GoogleDriveAuthorization.java`, `…/valueobject/DriveGrant.java`
- Create: `…/infrastructure/drive/GoogleDriveAuthorizationAdapter.java`,
  `GoogleDriveConfiguration.java`, `GoogleTokenResponse.java`, `GoogleAboutResponse.java`
- Create: `src/test/…/knowledge/FakeGoogleDriveAuthorizationConfiguration.java`

**Interfaces:**
- Consomme : `RefreshToken`, `DriveAuthorizationState`.
- Produit :

  ```java
  public interface GoogleDriveAuthorization {
      URI consentUrl(DriveAuthorizationState state);
      DriveGrant exchange(String authorizationCode);
  }
  ```

- [ ] **Step 1: Le port et son bouchon**

Écrire le port et **d'abord** `FakeGoogleDriveAuthorization`, sur le modèle de
`RecordingNotificationSenderConfiguration` : un `@TestConfiguration(proxyBeanMethods = false)`
avec un `@Bean @Primary`. Le bouchon rend une `DriveGrant` programmable, et sait lever
`GoogleDriveUnavailableException` sur demande. Il est **partagé par tout le contexte** et le
rollback du test ne le remet pas à zéro : un `clear()` en `@BeforeEach`, exactement comme le
`RecordingNotificationSender`.

- [ ] **Step 2: L'adapter réel**

`consentUrl` construit
`https://accounts.google.com/o/oauth2/v2/auth` avec `client_id`, `redirect_uri`,
`response_type=code`, `scope=https://www.googleapis.com/auth/drive.readonly`,
`access_type=offline`, `prompt=consent` et `state`. **Les deux derniers ne sont pas
décoratifs :** sans `access_type=offline` Google ne délivre aucun jeton de rafraîchissement, et
sans `prompt=consent` il n'en redélivre pas à une seconde autorisation du même compte.

`exchange` fait un `POST application/x-www-form-urlencoded` sur
`https://oauth2.googleapis.com/token`, puis un `GET https://www.googleapis.com/drive/v3/about?fields=user`
avec l'`access_token` reçu, et rend `DriveGrant(email, refreshToken)`. Une réponse sans
`refresh_token` est un **échec explicite** : c'est le symptôme d'un `access_type` oublié ou d'un
consentement déjà accordé, et laisser passer une connexion sans jeton la ferait mourir au premier
redémarrage.

Le `RestClient` est déclaré dans `GoogleDriveConfiguration`. **Poser des timeouts**, au même
titre que ceux d'Ollama et du courriel : un point de jeton qui accepte le TCP puis ne répond plus
gèlerait le thread. `spring.http.clients` porte déjà des valeurs globales — vérifier qu'elles
conviennent plutôt que d'en ajouter.

- [ ] **Step 3: Vérifier, formater, committer**

Aucun test n'atteint le vrai Google : ce qui se teste ici, c'est que le contexte démarre.

Message : `feat: un adapter parle à Google pour l'autorisation Drive`

---

### Task 4: Les commandes, la query et les quatre routes

**Files:**
- Create: les trois commandes et leurs handlers, la query et son handler, `DriveConnectionView`
- Create: les quatre contrôleurs et `DriveAuthorizationResponse`
- Create: `src/test/…/knowledge/infrastructure/web/DriveConnectionFlowTest.java`

- [ ] **Step 1: Écrire les tests qui échouent**

`DriveConnectionFlowTest` couvre **les six scénarios du ticket**, un test par scénario, plus les
refus d'accès :

| Méthode de test | Scénario |
|---|---|
| `connects_a_google_account_on_first_authorization` | Première connexion : la connexion apparaît active, avec l'adresse Google |
| `reports_that_no_access_was_granted_when_the_consent_is_denied` | Refus : redirection avec le code `refus`, aucune connexion créée |
| `replaces_the_previous_connection_rather_than_adding_a_second` | Le port ne rend qu'**une** connexion, à la nouvelle adresse |
| `forgets_the_connection_without_touching_the_documents` | Déconnexion : plus de connexion, les documents restent |
| `reports_a_connection_whose_access_was_revoked_as_needing_a_reconnection` | Statut `NEEDS_RECONNECTION` rendu par la route |
| `refuses_a_callback_whose_state_does_not_match_the_request` | Redirection avec le code `lien-invalide`, aucune connexion créée |
| `refuses_an_anonymous_request` | `401` sur les trois routes sous `/api` |

**Le scénario « autorisation révoquée depuis Google » n'a pas encore de déclencheur réel** : rien
ne lit le Drive avant DRIVE-2. Le test passe donc par le domaine — charger la connexion par son
port, appeler `markNeedsReconnection()`, sauver — puis vérifie que
`GET /api/drive/connection` le rend. **Le dire dans la PR** : le mécanisme existe, son
déclencheur arrive avec DRIVE-2.

Rappel de la règle : dans un test `@Transactional`, un appel HTTP **refusé** doit être le dernier
du test.

- [ ] **Step 2: Les commandes et la query**

`StartDriveAuthorization(ownerId)` — son handler ouvre la demande et la persiste. Le contrôleur a
besoin de l'URL, qu'une commande ne peut pas rendre ; il la demande **au port**, avec le `state`
qu'il relit par la query. **Non** : ce serait deux allers-retours et une lecture après écriture.
La forme retenue est celle-ci, et elle est la seule qui respecte « une commande ne retourne
rien » sans acrobatie :

> le contrôleur tire le `state` par `DriveAuthorizationState.random()`, dispatche
> `StartDriveAuthorization(ownerId, state)`, puis demande l'URL au port. Il n'a aucune règle
> métier : il assemble deux collaborateurs, comme `UploadDocumentController` lit des octets avant
> de dispatcher.

`CompleteDriveAuthorization(ownerId?, state, authorizationCode)` — **le propriétaire n'est pas
dans la commande** : il vient de la demande retrouvée par son `state`, et c'est tout l'intérêt du
dispositif, le callback n'étant pas authentifié. Son handler consomme la demande, appelle
`exchange`, puis crée **ou remplace** la connexion. `toString()` redéfini : la commande
transporte un code d'autorisation.

`DisconnectDrive(ownerId)` — supprime la connexion, et **rien d'autre** : les documents déjà
importés restent.

`FindDriveConnection(ownerId)` rend un `Optional<DriveConnectionView>` portant l'adresse Google,
le statut et l'instant de connexion — **jamais le jeton**.

- [ ] **Step 3: Les quatre contrôleurs**

Un mapping par classe, nommée par l'intention.

- `POST /api/drive/authorizations` → `201 { "authorizationUrl": "…" }`.
- `GET /drive/callback` → **`302` relatif** vers `/documents?drive=<code>`, avec
  `code ∈ { ok, refus, lien-invalide, echec }`. Relatif, comme `GET /verification` : le navigateur
  le résout contre l'origine, et l'application n'a aucune URL de front à connaître. Google renvoie
  `error=access_denied` sur un refus — c'est ce paramètre qui donne `refus`, pas une absence de
  `code`.
- `DELETE /api/drive/connection` → `204`, `404` si aucune connexion.
- `GET /api/drive/connection` → `200` avec la vue, `404` si aucune connexion.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: connecter, consulter et révoquer un compte Google Drive`

---

### Task 5: La configuration de développement, puis la documentation

**Files:**
- Modify: `compose.yaml`, `.env.example`, `CLAUDE.md`

- [ ] **Step 1: Les variables**

`.env.example` reçoit `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` et
`SECONDBRAIN_DRIVE_TOKEN_KEY`, **vides**, avec un commentaire disant où les obtenir et que
l'application OAuth doit être en statut « In production » chez Google — en « Testing », les
jetons de rafraîchissement expirent au bout de sept jours et la connexion meurt en silence.
`compose.yaml` les passe à `app` et à `worker`.

- [ ] **Step 2: Le récit**

Dans `CLAUDE.md`, une sous-section **« Le flux de la connexion à Google Drive »**, qui dit :

- les deux routes, et **pourquoi le callback vit hors de `/api`** — le lien vient de Google, sans
  jeton en en-tête, exactement comme `GET /verification` ;
- la redirection porteuse d'un **code**, pas d'un message (ADR-0017), et les quatre codes ;
- le `state` : nonce persisté, à usage unique, expirant, **seule protection du flux** puisque le
  projet n'a ni CSRF ni session (ADR-0003) ;
- le jeton de rafraîchissement chiffré par un `AttributeConverter`, sur le chemin d'`Email` et de
  `Checksum` — et l'écart assumé du `@Component` sur ce converter, avec sa raison ;
- `access_type=offline` et `prompt=consent`, sans lesquels il n'y a pas de jeton de
  rafraîchissement ;
- l'adresse Google lue par `about.get`, et non par un scope de plus ;
- **ce qui reste hors du dépôt** : les identifiants OAuth et le statut « In production ».

Compléter la section « Persistance » (les deux tables, le troisième `AttributeConverter` invisible
au code) et « Stack et versions » (aucune dépendance nouvelle, l'adapter est écrit à la main).

- [ ] **Step 3: Committer**

Message : `docs: documente la connexion à un compte Google Drive`
