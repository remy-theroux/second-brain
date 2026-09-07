# Retélécharger le fichier d'origine — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre le fichier d'origine d'un document par `GET /api/documents/{id}/content`, et
le déclencher d'un clic depuis la liste comme depuis l'écran de détail.

**Architecture:** Une query `FindDocumentContent` relit le document par son propriétaire puis
son original par le port `DocumentStorage` déjà en place ; un contrôleur mono-route l'habille
d'un `Content-Type` déduit du format et d'un `Content-Disposition: attachment`. Côté front,
`src/api/client.js` rend un `Blob` et un composant partagé `DownloadDocumentButton` le remet
au navigateur par une ancre temporaire — le jeton voyageant en en-tête, un simple lien ne
peut pas faire ce travail.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 (MVC, Security) · AWS SDK v2 (déjà en place) ·
JUnit 5 + AssertJ + Testcontainers · Vue 3 · PrimeVue 4 (Aura) · Vitest (jsdom).

**Spec:** `docs/superpowers/specs/2026-09-07-telechargement-original-design.md`

## Global Constraints

- **Worktree :** tout le travail a lieu dans
  `/home/remy-theroux/projects/second-brain-telechargement-original`, sur la branche
  `feat/telechargement-original`. Ne jamais éditer `~/projects/second-brain`.
- **Aucun JDK ni Node sur l'hôte.** Définir ces deux fonctions **une fois** au début de la
  session, et les utiliser pour toute commande Gradle ou npm :

  ```bash
  gtest() {
    docker run --rm --network host \
      -v "$PWD":/app -w /app \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v second-brain-gradle-home-telechargement-original:/home/gradle/.gradle \
      gradle:jdk25 gradle --no-daemon "$@"
  }

  gfront() {
    docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp \
      -v "$PWD/frontend":/app -w /app \
      node:24-alpine "$@"
  }
  ```

- **Le `Makefile` doit viser le cache de ce worktree.** `GRADLE_HOME_VOLUME` vaut
  `second-brain-gradle-home` par défaut, c'est-à-dire le volume du dépôt principal : sans ce
  réglage, `make format-back` et `gtest` travaillent sur deux caches différents et se
  bloquent sur le verrou de Gradle dès qu'ils se croisent. Exporter, une fois par session :

  ```bash
  export GRADLE_HOME_VOLUME=second-brain-gradle-home-telechargement-original
  ```

- **`gtest` et `docker compose up` ne cohabitent pas** : ils verrouillent le même `.gradle/`.
  Si la pile de ce worktree tourne, `docker compose down` avant tout `gtest`.
- **Langue :** le code, les commentaires, la Javadoc et les noms de méthodes de test sont en
  **anglais**. Les messages d'exception métier, les libellés d'écran et les messages de commit
  sont en **français**. Les libellés `describe`/`it` de Vitest sont en anglais.
- **Commentaires :** l'exception, jamais l'habitude. Trois lignes est un plafond. Le
  raisonnement vit dans la spec, pas dans le code.
- **Aucun ADR n'est écrit dans ce plan.** La décision 6 de la spec en appellerait un ; les
  règles du dépôt interdisent de le rédiger sans accord préalable du propriétaire.
- **Aucune migration Flyway.** Rien de nouveau n'est persisté.
- **Formatage :** `make format-back` avant tout commit qui touche du Java, `make format-front`
  avant tout commit qui touche `frontend/`. Ne pas se battre avec palantir-java-format ni avec
  Prettier.
- **Commits :** préfixe conventionnel en minuscule, description en français, un commit par
  tâche, tests verts.
- **Ne pas toucher aux versions** de `gradle/libs.versions.toml` ni de
  `frontend/package.json`. Aucune dépendance nouvelle n'est requise par ce plan.

## Fichiers

**Back — créés**

| Fichier | Responsabilité |
|---|---|
| `…/knowledge/domain/exception/MissingDocumentContentException.java` | Le refus « l'original a disparu du stockage » |
| `…/knowledge/application/query/FindDocumentContent.java` | La query : identifiant du document, identifiant du propriétaire |
| `…/knowledge/application/query/DocumentContentView.java` | Le modèle de lecture : nom, format, octets |
| `…/knowledge/application/query/FindDocumentContentHandler.java` | Relit le document cloisonné, puis son original |
| `…/knowledge/infrastructure/web/FindDocumentContentController.java` | `GET /api/documents/{id}/content` |
| `src/test/…/knowledge/infrastructure/web/FindDocumentContentControllerTest.java` | Les scénarios de la route, de bout en bout |

**Back — modifiés**

| Fichier | Modification |
|---|---|
| `…/knowledge/domain/valueobject/DocumentFormat.java` | Un type MIME par constante, plus l'accesseur `mediaType()` |
| `src/test/…/knowledge/domain/valueobject/DocumentFormatTest.java` | Deux tests de plus |

**Front — créés**

| Fichier | Responsabilité |
|---|---|
| `frontend/src/components/DownloadDocumentButton.vue` | Le geste complet : appel, sauvegarde, état occupé, émission d'erreur |

**Front — modifiés**

| Fichier | Modification |
|---|---|
| `frontend/src/api/client.js` | `fetchDocumentContent(token, id)` |
| `frontend/src/api/client.spec.js` | Un `describe` de plus |
| `frontend/src/views/DocumentsView.vue` | Le bouton dans la colonne d'actions |
| `frontend/src/views/DocumentDetailView.vue` | Le bouton dans la barre du haut, et l'extraction de `handle` |
| `frontend/src/views/DesignSystemView.vue` | La vignette du composant partagé |

**Documentation — modifiée**

| Fichier | Modification |
|---|---|
| `CLAUDE.md` | L'arborescence (query, exception, composants front) et le récit de la route |

---

### Task 1: Le type MIME entre dans `DocumentFormat`

**Files:**
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormat.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormatTest.java`

**Interfaces:**
- Consumes: rien.
- Produces: `String DocumentFormat.mediaType()` — rendu par les tâches 2 et 3.

- [ ] **Step 1: Écrire les tests qui échouent**

Ajouter ces deux méthodes à la fin de `DocumentFormatTest`, **avant** l'accolade fermante de
la classe. Le `@CsvSource` existant du fichier montre déjà comment s'écrit un test paramétré
ici ; ne pas réordonner ce qui s'y trouve.

```java
    @ParameterizedTest
    @CsvSource({
        "PDF, application/pdf",
        "MARKDOWN, text/markdown",
        "TEXT, text/plain",
        "DOCX, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    })
    void announces_the_media_type_of_each_format(DocumentFormat format, String expected) {
        assertThat(format.mediaType()).isEqualTo(expected);
    }

    @Test
    void every_format_announces_a_media_type() {
        assertThat(DocumentFormat.values())
                .allSatisfy(format -> assertThat(format.mediaType()).isNotBlank());
    }
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormatTest"
```

Attendu : **échec de compilation**, `cannot find symbol: method mediaType()`.

- [ ] **Step 3: Ajouter le type MIME à l'énumération**

Remplacer, dans `DocumentFormat.java`, les constantes, les champs et le constructeur par :

```java
public enum DocumentFormat {
    PDF(".pdf", "application/pdf", DocumentType.TEXTUAL),
    MARKDOWN(".md", "text/markdown", DocumentType.TEXTUAL),
    TEXT(".txt", "text/plain", DocumentType.TEXTUAL),
    DOCX(
            ".docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocumentType.TEXTUAL);

    private final String extension;
    private final String mediaType;
    private final DocumentType type;

    DocumentFormat(String extension, String mediaType, DocumentType type) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }
```

Ne rien changer d'autre : `type()`, `of`, `fromFilename` et `acceptedExtensions` restent
tels quels.

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormatTest"
```

Attendu : **PASS**, les dix tests de la classe.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormat.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/domain/valueobject/DocumentFormatTest.java
git commit -m "feat: chaque format de document annonce son type MIME"
```

---

### Task 2: La query qui relit l'original

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/MissingDocumentContentException.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/FindDocumentContent.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/DocumentContentView.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/FindDocumentContentHandler.java`

**Interfaces:**
- Consumes: `DocumentFormat.mediaType()` (tâche 1) — indirectement, par le format porté dans
  la vue ; `DocumentRepository.findByIdAndOwnerId(UUID, UUID)` et
  `DocumentStorage.read(UUID)`, tous deux existants.
- Produces, pour la tâche 3 :
  - `record FindDocumentContent(UUID documentId, UUID ownerId) implements Query<Optional<DocumentContentView>>`
  - `record DocumentContentView(String filename, DocumentFormat format, byte[] content)`
  - `MissingDocumentContentException`, avec la constante publique `MESSAGE`.

Il n'y a **pas de test dédié dans cette tâche** : le chemin réel de production passe par la
route, et c'est la tâche 3 qui l'exerce de bout en bout. Un test qui appellerait le handler
en direct contredirait la règle « dispatcher via le bus plutôt qu'appeler le handler en
direct ». Cette tâche livre donc du code qui compile et se câble au démarrage ; sa
vérification est le démarrage du contexte Spring, qui construit la table de routage des bus
et échoue s'il y a deux handlers pour un même message.

- [ ] **Step 1: Écrire l'exception métier**

`src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/MissingDocumentContentException.java` :

```java
package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class MissingDocumentContentException extends RuntimeException {

    public static final String MESSAGE = "L'original de ce document n'est plus disponible.";

    public MissingDocumentContentException() {
        super(MESSAGE);
    }
}
```

Le message est en français : c'est une exception métier, elle est affichable telle quelle.

- [ ] **Step 2: Écrire la query et son modèle de lecture**

`…/application/query/FindDocumentContent.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

public record FindDocumentContent(UUID documentId, UUID ownerId) implements Query<Optional<DocumentContentView>> {}
```

`…/application/query/DocumentContentView.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;

public record DocumentContentView(String filename, DocumentFormat format, byte[] content) {}
```

Ce modèle de lecture n'est **jamais sérialisé en JSON** : le contrôleur en tire un corps
binaire et deux en-têtes. C'est aussi pour ça qu'un `record` portant un tableau ne pose ici
aucun problème — rien ne le compare, rien ne le hache.

- [ ] **Step 3: Écrire le handler**

`…/application/query/FindDocumentContentHandler.java` :

```java
package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.MissingDocumentContentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class FindDocumentContentHandler implements QueryHandler<FindDocumentContent, Optional<DocumentContentView>> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;

    public FindDocumentContentHandler(DocumentRepository documentRepository, DocumentStorage documentStorage) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
    }

    // An empty Optional means "no such document for this owner". A row without its object is
    // not an absence but a broken invariant, so it throws — see the design, decision 6.
    @Override
    public Optional<DocumentContentView> handle(FindDocumentContent query) {
        return documentRepository
                .findByIdAndOwnerId(query.documentId(), query.ownerId())
                .map(document -> new DocumentContentView(
                        document.getFilename(),
                        document.getFormat(),
                        documentStorage
                                .read(document.getId())
                                .orElseThrow(MissingDocumentContentException::new)));
    }
}
```

**Aucun `@Transactional` sur ce handler** : la transaction appartient au bus, et l'annoter
casserait la résolution de son type générique au démarrage.

- [ ] **Step 4: Vérifier que le contexte démarre et que la suite reste verte**

```bash
gtest test --tests "xyz.sterenn.secondbrain.SecondBrainApplicationTests"
```

Attendu : **PASS**. Ce test charge le contexte complet, donc construit la table de routage
du query bus avec le nouveau handler.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/exception/MissingDocumentContentException.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/FindDocumentContent.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/DocumentContentView.java \
        src/main/java/xyz/sterenn/secondbrain/knowledge/application/query/FindDocumentContentHandler.java
git commit -m "feat: une query relit l'original d'un document, cloisonnée par propriétaire"
```

---

### Task 3: La route `GET /api/documents/{id}/content`

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentContentController.java`
- Test: `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentContentControllerTest.java`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `FindDocumentContent`, `DocumentContentView`, `MissingDocumentContentException`
  (tâche 2) ; `DocumentFormat.mediaType()` (tâche 1) ; `JwtSubject.accountId(Jwt)`,
  `ErrorResponse(String message)`, `DocumentNotFoundException.MESSAGE`,
  `DocumentStorageUnavailableException` — tous existants.
- Produces, pour la tâche 4 : la route `GET /api/documents/{id}/content`, qui rend `200` avec
  le corps binaire, `404 {"message": …}` dans les deux cas d'absence, `503 {"message": …}` si
  le stockage ne répond pas, et `401` sans jeton.

- [ ] **Step 1: Écrire le test qui échoue**

Créer
`src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentContentControllerTest.java`.

Le montage est celui de `FindDocumentControllerTest`, voisin de fichier : `@SpringBootTest`
+ `@Import(TestcontainersConfiguration.class)`, un compte vérifié en `@BeforeEach`, le vidage
du bucket en `@AfterEach` — `@Transactional` annule la base, jamais le stockage objet.

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindDocumentContentControllerTest {

    private static final String PASSWORD = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID alice;
    private String aliceToken;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    @AfterEach
    void erase_the_originals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void returns_the_uploaded_bytes_unchanged() throws Exception {
        Document document = upload(aliceToken, alice, "structure.md", Fixtures.STRUCTURED_MD);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Fixtures.read(Fixtures.STRUCTURED_MD)));
    }

    @Test
    void announces_the_media_type_of_the_format() throws Exception {
        Document document = upload(aliceToken, alice, "signets.pdf", Fixtures.BOOKMARKS_PDF);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")));
    }

    @Test
    void offers_the_file_as_an_attachment_under_its_original_name() throws Exception {
        Document document = upload(aliceToken, alice, "notes.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("notes.txt")));
    }

    @Test
    void percent_encodes_an_accented_name_in_the_header() throws Exception {
        Document document = upload(aliceToken, alice, "été.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''%C3%A9t%C3%A9.txt")));
    }

    @Test
    void returns_the_original_of_a_document_whose_processing_failed() throws Exception {
        Document document = upload(aliceToken, alice, "scan.txt", Fixtures.RAW_TXT);
        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Fixtures.read(Fixtures.RAW_TXT)));
    }

    @Test
    void says_the_original_is_gone_rather_than_the_document() throws Exception {
        Document document = upload(aliceToken, alice, "disparu.txt", Fixtures.RAW_TXT);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("L'original de ce document n'est plus disponible."));
    }

    @Test
    void makes_an_unknown_id_not_found() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Ce document est introuvable dans votre base de connaissance."));
    }

    @Test
    void makes_the_document_of_another_account_not_found() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        Document bobsDocument =
                upload(KnowledgeFixture.token(accessTokenIssuer, bob), bob, "chez-bob.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + bobsDocument.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuses_the_download_without_a_token() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID() + "/content"))
                .andExpect(status().isUnauthorized());
    }

    private Document upload(String token, UUID ownerId, String filename, String fixture) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file", filename, "application/octet-stream", Fixtures.read(fixture)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(ownerId).stream()
                .filter(document -> document.getFilename().equals(filename))
                .findFirst()
                .orElseThrow();
    }
}
```

Trois pièges de montage à ne pas « corriger » :

1. `KnowledgeFixture.emptyTheOriginals` est appelé **dans** le test de l'original disparu,
   et pas seulement en `@AfterEach` : c'est le seul moyen de fabriquer une ligne sans son
   objet, `@Transactional` n'annulant jamais le stockage objet.
2. Chaque test qui se termine par un refus n'a **plus aucun appel HTTP après lui** :
   l'exception métier marque la transaction englobante « rollback-only », et la requête
   suivante échouerait sur une `UnexpectedRollbackException`.
3. Les fixtures binaires (`signets.pdf`) sont versionnées ; si elles manquent, `Fixtures.read`
   le dit et la commande est `gtest generateFixtures`.

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.FindDocumentContentControllerTest"
```

Attendu : **FAIL** — les huit premiers tests répondent `404` (aucune route ne correspond à
`/content`), le dernier passe déjà puisque `SecurityConfig` refuse par défaut sous `/api/**`.

- [ ] **Step 3: Écrire le contrôleur**

`src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentContentController.java` :

```java
package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.DocumentContentView;
import xyz.sterenn.secondbrain.knowledge.application.query.FindDocumentContent;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.MissingDocumentContentException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

@RestController
public class FindDocumentContentController {

    private final QueryBus queryBus;

    public FindDocumentContentController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/documents/{id}/content")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> content(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        try {
            return queryBus.ask(new FindDocumentContent(id, JwtSubject.accountId(jwt)))
                    .<ResponseEntity<Object>>map(FindDocumentContentController::attachment)
                    .orElseGet(() -> notFound(DocumentNotFoundException.MESSAGE));
        } catch (MissingDocumentContentException goneOriginal) {
            return notFound(goneOriginal.getMessage());
        } catch (DocumentStorageUnavailableException unreachableStorage) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Le téléchargement est momentanément indisponible : le stockage des "
                            + "originaux n'a pas répondu. Réessayez dans quelques instants."));
        }
    }

    private static ResponseEntity<Object> attachment(DocumentContentView content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.format().mediaType()))
                .contentLength(content.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(content.content());
    }

    private static ResponseEntity<Object> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(message));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

Rien à déclarer dans `SecurityConfig` : `/api/**` est refusé par défaut, et c'est
exactement ce qu'on veut ici.

- [ ] **Step 4: Lancer le test pour vérifier qu'il passe**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.FindDocumentContentControllerTest"
```

Attendu : **PASS**, les neuf tests.

Si `percent_encodes_an_accented_name_in_the_header` échoue sur la casse ou la forme exacte
de l'échappement, **regarder la valeur réellement produite** avant de toucher au contrôleur :
`ContentDisposition` de Spring fait foi, c'est l'assertion qu'il faut aligner sur lui.

- [ ] **Step 5: Lancer toute la suite back**

```bash
gtest test
```

Attendu : **PASS**. Aucune régression n'est attendue — rien d'existant n'a changé de
signature.

- [ ] **Step 6: Documenter la route dans `CLAUDE.md`**

Trois retouches, toutes dans `CLAUDE.md`.

**(a)** Dans l'arborescence, ligne 274, ajouter l'exception à la liste du contexte
`knowledge` :

```
│   │   │                    InvalidQuestionException, LlmUnavailableException,
│   │   │                    MissingDocumentContentException,
```

**(b)** Dans l'arborescence, ligne 287, ajouter la query :

```
│   │   └── query/           ListDocuments + DocumentView, FindDocument + DocumentDetailView
│   │                        + TextExtractionView, SearchChunks + ChunkMatchView,
│   │                        FindDocumentContent + DocumentContentView
```

**(c)** Dans la section « Le flux du dépôt d'un document », **juste après** le paragraphe qui
commence par ``` `GET /api/documents/{id}` rend un document **et ce qui en a été extrait** ```
et se termine par « une query ne lève pas. », insérer :

```markdown
`GET /api/documents/{id}/content` rend le fichier **tel qu'il a été déposé**, sous son nom
d'origine — `Content-Disposition: attachment`, nom encodé en RFC 5987 parce qu'un accent
dans un en-tête HTTP sans encodage est un octet non spécifié. Le `Content-Type` vient de
`DocumentFormat.mediaType()` : le type MIME est une propriété du format, au même titre que
son extension, et non le `Content-Type` du multipart, qui n'a jamais été stocké. La route ne
regarde **jamais le statut** : un document dont l'extraction a échoué n'a plus que son
original, c'est précisément ce qu'on vient y chercher.

Deux absences, deux messages, un seul code. Le document inconnu rend le `404` habituel ; un
document bien présent dont l'objet a disparu du stockage rend `404 {"message": "L'original
de ce document n'est plus disponible."}` — dire « document introuvable » mentirait, l'écran
le montre. Et cette seconde absence est la seule query du contexte qui **lève** : une ligne
`knowledge_documents` sans son objet n'est pas un résultat vide, c'est une rupture
d'invariant qu'aucun chemin nominal ne produit (voir la spec du téléchargement, décision 6).
Le stockage injoignable, lui, rend `503`, comme la recherche pour Ollama.
```

- [ ] **Step 7: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentContentController.java \
        src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/FindDocumentContentControllerTest.java \
        CLAUDE.md
git commit -m "feat: une route rend le fichier d'origine d'un document"
```

---

### Task 4: L'appel HTTP côté front

**Files:**
- Modify: `frontend/src/api/client.js`
- Test: `frontend/src/api/client.spec.js`

**Interfaces:**
- Consumes: la route `GET /api/documents/{id}/content` (tâche 3) ; `UnauthorizedError`, déjà
  exportée par `client.js`.
- Produces, pour la tâche 5 :
  `async function fetchDocumentContent(token: string, id: string): Promise<Blob>` — lève
  `UnauthorizedError` sur `401`, une `Error` portant le message du serveur sinon.

- [ ] **Step 1: Écrire les tests qui échouent**

Deux ajouts à `frontend/src/api/client.spec.js`.

D'abord, ajouter `fetchDocumentContent` à la liste d'imports en tête de fichier (la liste est
alphabétique : il se place entre `fetchDocument` et `listDocuments`).

Ensuite, ajouter à côté du helper `jsonResponse` existant, juste en dessous :

```js
// A binary response: only the status and the blob matter for this module.
function blobResponse(status, blob) {
  return {
    ok: status >= 200 && status < 300,
    status,
    blob: () => Promise.resolve(blob),
  }
}
```

Enfin, ajouter ce `describe` **à l'intérieur** du `describe('knowledge base', …)`, après le
bloc `describe('deleting a document', …)` :

```js
  describe('downloading the original file', () => {
    it('reads the content route with the bearer token', async () => {
      const file = new Blob(['bonjour'], { type: 'text/plain' })
      fetch.mockResolvedValue(blobResponse(200, file))

      const result = await fetchDocumentContent('jeton-abc', 'doc-1')

      const [url, options] = fetch.mock.calls[0]
      expect(url).toBe('/api/documents/doc-1/content')
      expect(options.headers.Authorization).toBe('Bearer jeton-abc')
      expect(result).toBe(file)
    })

    it('translates a 401 into an expired session', async () => {
      fetch.mockResolvedValue(blobResponse(401, null))

      await expect(fetchDocumentContent('jeton-perime', 'doc-1')).rejects.toThrow(UnauthorizedError)
    })

    it('surfaces the message of a vanished original', async () => {
      fetch.mockResolvedValue(
        jsonResponse(404, { message: "L'original de ce document n'est plus disponible." }),
      )

      await expect(fetchDocumentContent('jeton-abc', 'doc-1')).rejects.toThrow(
        "L'original de ce document n'est plus disponible.",
      )
    })

    it('falls back to its own message when the body is not JSON', async () => {
      fetch.mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new SyntaxError('Unexpected token <')),
      })

      await expect(fetchDocumentContent('jeton-abc', 'doc-1')).rejects.toThrow(
        "Le fichier n'a pas pu être téléchargé.",
      )
    })
  })
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gfront npx vitest run src/api/client.spec.js
```

Attendu : **FAIL** — `fetchDocumentContent is not a function` (l'import est `undefined`).

- [ ] **Step 3: Écrire la fonction**

Ajouter à `frontend/src/api/client.js`, **après** `fetchDocument` et avant `uploadDocument` :

```js
/**
 * Reads the original file of a document, as it was uploaded. Returns a `Blob`: handing it to
 * the browser is a screen matter, this module only knows the call.
 */
export async function fetchDocumentContent(token, id) {
  const response = await fetch(`/api/documents/${id}/content`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (response.ok) {
    return response.blob()
  }

  // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
  // must not replace the business message with a syntax error.
  const payload = await response.json().catch(() => null)
  // The two 404 and the 503 each carry their message, displayable as is.
  throw new Error(payload?.message ?? "Le fichier n'a pas pu être téléchargé.")
}
```

- [ ] **Step 4: Lancer les tests pour vérifier qu'ils passent**

```bash
gfront npx vitest run src/api/client.spec.js
```

Attendu : **PASS**, tous les tests du fichier.

- [ ] **Step 5: Formater et committer**

```bash
make format-front
git add frontend/src/api/client.js frontend/src/api/client.spec.js
git commit -m "feat: le front sait lire le fichier d'origine d'un document"
```

---

### Task 5: Le composant partagé `DownloadDocumentButton`

**Files:**
- Create: `frontend/src/components/DownloadDocumentButton.vue`
- Modify: `frontend/src/views/DesignSystemView.vue`

**Interfaces:**
- Consumes: `fetchDocumentContent(token, id)` (tâche 4) ; `useAuthStore()` pour le jeton.
- Produces, pour la tâche 6 : un composant à trois props et un événement.

  | Prop | Type | Défaut |
  |---|---|---|
  | `documentId` | `String`, requise | — |
  | `filename` | `String`, requise | — |
  | `disabled` | `Boolean` | `false` |

  Événement `error`, portant l'erreur telle que `fetchDocumentContent` l'a levée — donc une
  `UnauthorizedError` reconnaissable par `instanceof` chez l'appelant.

Ce composant n'a **pas de test unitaire** : la règle front proscrit les tests de rendu, et le
contrôle visuel se fait sur `/design-system` (ADR-0016). Ce que ce composant a de fragile —
la lecture HTTP — est testé à la tâche 4.

- [ ] **Step 1: Écrire le composant**

`frontend/src/components/DownloadDocumentButton.vue` :

```vue
<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import { fetchDocumentContent } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  documentId: { type: String, required: true },
  filename: { type: String, required: true },
  disabled: { type: Boolean, default: false },
})

// The screen keeps the signing out: a shared component does not push a route. It only says
// that the call failed, and the view passes the error to its own handler.
const emit = defineEmits(['error'])

const auth = useAuthStore()
const busy = ref(false)

// The token travels in a header (ADR-0003), so a plain link would fetch nothing: the body is
// read by `fetch`, then handed to the browser through a temporary anchor.
async function download() {
  busy.value = true
  try {
    save(await fetchDocumentContent(auth.token, props.documentId))
  } catch (error) {
    emit('error', error)
  } finally {
    busy.value = false
  }
}

function save(blob) {
  const url = URL.createObjectURL(blob)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = props.filename
  window.document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // Revoked on the next tick: revoking within the same one cancels the download the click
  // has only just started, in several browsers.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
</script>

<template>
  <Button
    type="button"
    icon="pi pi-download"
    text
    rounded
    :loading="busy"
    :disabled="disabled || busy"
    :aria-label="`Télécharger ${filename}`"
    @click="download"
  />
</template>
```

`window.document` et non `document` : le composant est appelé depuis `DocumentDetailView`, où
une `ref` nommée `document` existe déjà. Le préfixe ne coûte rien et retire toute ambiguïté
au lecteur.

- [ ] **Step 2: Ajouter la vignette au design system**

Dans `frontend/src/views/DesignSystemView.vue`, ajouter l'import à la suite des autres imports
de composants du projet :

```js
import DownloadDocumentButton from '@/components/DownloadDocumentButton.vue'
```

Puis insérer cette section **juste après** la section
`<h2>Statut de document — DocumentStatusTag</h2>` et avant `<h2>Tableau — DataTable</h2>` :

```html
    <section>
      <h2>Téléchargement d'un original — DownloadDocumentButton</h2>
      <p class="muted">
        Le même geste pour la liste et pour le détail : le jeton voyageant en en-tête, un lien
        ne peut rien télécharger — le fichier est lu par <code>src/api/</code> puis remis au
        navigateur par une ancre temporaire. Le bouton porte son état occupé ; il émet ses
        erreurs, la vue garde la déconnexion. Ici, le clic part vraiment et échoue sans
        conséquence : le catalogue n'écoute pas l'événement.
      </p>
      <div class="row">
        <DownloadDocumentButton document-id="00000000-0000-0000-0000-000000000000" filename="rapport.pdf" />
        <DownloadDocumentButton
          document-id="00000000-0000-0000-0000-000000000000"
          filename="rapport.pdf"
          disabled
        />
      </div>
    </section>
```

- [ ] **Step 3: Vérifier que le front compile**

```bash
make format-front
gfront npm run build
```

Attendu : **succès**. C'est le seul contrôle qui compile les templates — aucun test ne les
rend.

- [ ] **Step 4: Vérifier que la suite front reste verte**

```bash
gfront npm run test:unit
```

Attendu : **PASS**.

- [ ] **Step 5: Regarder le catalogue**

Démarrer la pile de ce worktree et ouvrir <http://localhost:8081/design-system> :

```bash
docker compose up --build -d
```

Vérifier à l'œil : l'icône de téléchargement est là, le second bouton est bien grisé,
l'espacement suit celui des sections voisines. Puis arrêter la pile avant tout `gtest` :

```bash
docker compose down
```

- [ ] **Step 6: Committer**

```bash
git add frontend/src/components/DownloadDocumentButton.vue frontend/src/views/DesignSystemView.vue
git commit -m "feat: un composant partagé porte le téléchargement d'un original"
```

---

### Task 6: Le bouton sur les deux écrans

**Files:**
- Modify: `frontend/src/views/DocumentsView.vue`
- Modify: `frontend/src/views/DocumentDetailView.vue`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `DownloadDocumentButton` (tâche 5), avec ses props `document-id`, `filename`,
  `disabled` et son événement `error`.
- Produces: rien — c'est la dernière tâche.

- [ ] **Step 1: Câbler la liste**

Dans `frontend/src/views/DocumentsView.vue`, ajouter l'import sous celui de
`DocumentStatusTag` :

```js
import DownloadDocumentButton from '@/components/DownloadDocumentButton.vue'
```

Puis, dans la colonne d'actions du `DataTable`, insérer le bouton **avant** celui de l'œil :

```html
        <template #body="{ data }">
          <DownloadDocumentButton
            :document-id="data.id"
            :filename="data.filename"
            :disabled="busy"
            @error="handle"
          />
          <Button
            type="button"
            icon="pi pi-eye"
            text
            rounded
            :aria-label="`Voir ${data.filename}`"
            @click="router.push({ name: 'document', params: { id: data.id } })"
          />
```

`handle` existe déjà dans cette vue et fait exactement ce qu'il faut : déconnexion sur
`UnauthorizedError`, message affiché sinon.

Ne pas effacer `errorMessage` au clic : un téléchargement n'annule pas le refus d'un dépôt
qui l'a précédé.

- [ ] **Step 2: Câbler le détail**

Dans `frontend/src/views/DocumentDetailView.vue`, trois retouches.

**(a)** Ajouter l'import sous celui de `DocumentStatusTag` :

```js
import DownloadDocumentButton from '@/components/DownloadDocumentButton.vue'
```

**(b)** Extraire la gestion d'erreur de `load`, qui la portait en propre, et remplacer les
deux par :

```js
// The server prevails: a 401 signs out, whatever the browser thinks. Any other failure is
// displayed — including the 404, whose message comes from the server.
async function handle(error) {
  if (error instanceof UnauthorizedError) {
    auth.logout()
    await router.push({ name: 'login' })
    return
  }
  errorMessage.value = error.message
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    document.value = await fetchDocument(auth.token, route.params.id)
  } catch (error) {
    await handle(error)
  } finally {
    loading.value = false
  }
}
```

C'est le même `handle` que dans `DocumentsView`, et c'est voulu : les deux vues traitent
l'erreur pour elles-mêmes, le composant n'en traite aucune.

**(c)** Remplacer le `<div>` qui n'entoure que le bouton « Documents » par une barre à deux
extrémités :

```html
    <div class="toolbar">
      <Button
        type="button"
        icon="pi pi-arrow-left"
        label="Documents"
        text
        @click="router.push({ name: 'documents' })"
      />
      <DownloadDocumentButton
        v-if="document"
        :document-id="document.id"
        :filename="document.filename"
        @error="handle"
      />
    </div>
```

Le `v-if="document"` n'est pas décoratif : tant que la lecture n'a pas abouti, il n'y a ni
identifiant ni nom à passer, et les deux props sont requises.

Ajouter enfin, dans le `<style scoped>` de la vue, à la suite de `.document-detail` :

```css
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
```

- [ ] **Step 3: Vérifier que le front compile et que les tests passent**

```bash
make format-front
gfront npm run build
gfront npm run test:unit
```

Attendu : **succès** puis **PASS**.

- [ ] **Step 4: Documenter le front dans `CLAUDE.md`**

Deux retouches.

**(a)** Dans l'arborescence du front, ligne 334, ajouter le composant :

```
├── src/components/          partagé entre vues : les deux layouts, FormField, PageTitle,
│                            DocumentStatusTag (libellé et sévérité d'un statut),
│                            DownloadDocumentButton (le geste de retélécharger un original)
```

**(b)** À la fin du paragraphe consacré à `DocumentDetailView` — celui qui se termine par
« le motif était copié, il est devenu un composant. » — ajouter :

```markdown
Les deux écrans portent le même bouton de téléchargement, `DownloadDocumentButton` : le jeton
voyageant en en-tête, un `<a href>` ne rapporterait qu'un `401`, et le fichier est donc lu par
`fetchDocumentContent` puis remis au navigateur par une ancre `download` fabriquée, cliquée et
révoquée. Le composant porte l'appel et son état occupé mais **pas la déconnexion** : il émet
son erreur, et chaque vue la passe à son propre `handle`. Le nom du fichier lui est passé en
prop plutôt que décodé du `Content-Disposition` — les deux valeurs viennent du même
`GET /api/documents`, dans la même page.
```

- [ ] **Step 5: Vérifier l'ensemble, des deux côtés**

Arrêter la pile si elle tourne, puis :

```bash
docker compose down
make check
```

Attendu : formatage conforme et **toute** la suite verte, back et front.

- [ ] **Step 6: Constater le geste dans l'application**

```bash
docker compose up --build -d
```

Sur <http://localhost:8081> : se connecter, déposer un document, cliquer l'icône de
téléchargement depuis la liste, puis depuis l'écran de détail. Vérifier que le fichier arrive
sous son nom d'origine et qu'il s'ouvre. Refaire l'essai avec un nom accentué — c'est le seul
endroit où l'encodage RFC 5987 se constate pour de bon.

```bash
docker compose down
```

- [ ] **Step 7: Committer**

```bash
git add frontend/src/views/DocumentsView.vue frontend/src/views/DocumentDetailView.vue CLAUDE.md
git commit -m "feat: le fichier d'origine se retélécharge depuis la liste et depuis le détail"
```
