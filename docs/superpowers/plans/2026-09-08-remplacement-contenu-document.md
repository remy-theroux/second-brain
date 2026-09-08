# Remplacer le contenu d'un document — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** `PUT /api/documents/{id}` remplace le contenu d'un document déjà ingéré **sans lui
faire perdre son identité**, et ne coûte rien quand le contenu est strictement le même.

**Architecture:** Une commande `ReplaceDocumentContent` relit le document cloisonné, calcule
l'empreinte du nouveau contenu et **s'arrête là si elle est inchangée** — c'est le scénario le
plus fréquent d'une synchronisation, et il ne doit ni écrire, ni vectoriser, ni publier quoi que
ce soit. Sinon elle écrase l'original, remet le document en `PENDING` et annonce
`DocumentContentReplaced`, un événement **nouveau** que le worker traite exactement comme un
dépôt : il dispatche `ExtractDocumentText`. Le pipeline d'ingestion n'est pas touché — il
efface déjà le texte et les extraits précédents avant d'écrire les siens, parce qu'AMQP livre au
moins une fois.

Deux points de conception à connaître :

- **Pourquoi un événement nouveau plutôt que republier `DocumentUploaded`.** La clé de routage
  se dérive du nom (`knowledge.document-content.replaced`), et le binding `knowledge.#` la
  couvre déjà. Republier `DocumentUploaded` ferait mentir un fait au passé : le document n'a pas
  été déposé, son contenu a été remplacé. Un futur consommateur qui compterait les dépôts
  compterait faux. Le coût est un `@RabbitHandler` de plus, qui dispatche la même commande.
- **Pourquoi `replace` sur le port de stockage plutôt qu'un `delete` suivi d'un `store`.**
  `store` refuse délibérément d'écraser (« An original is already stored ») ; ce garde-fou vise
  un handler qui appellerait `store` deux fois, et il doit rester. Un remplacement est une
  intention distincte : un seul `PutObject`, qui écrase, plutôt que deux appels dont le premier
  laisse une fenêtre où le document n'a plus d'original.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 (MVC, Security) · Spring AMQP · AWS SDK v2 ·
JUnit 5 + AssertJ + Testcontainers. Aucune dépendance nouvelle, **aucune migration Flyway** :
rien de nouveau n'est persisté.

**Ticket:** RAG-7 — Ré-ingérer un document modifié. Prérequis strict de DRIVE-5.

## Global Constraints

- **Branche :** `feat/rag-7-remplacement-contenu`, dans `/home/remy-theroux/projects/second-brain`.
- **Aucun JDK ni Gradle sur l'hôte.** Définir cette fonction **une fois** au début de la session :

  ```bash
  gtest() {
    docker run --rm --network host \
      -v "$PWD":/app -w /app \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v second-brain-gradle-home:/home/gradle/.gradle \
      gradle:jdk25 gradle --no-daemon "$@"
  }
  ```

- **`gtest` et `docker compose up` ne cohabitent pas** : ils verrouillent le même `.gradle/`.
  `docker compose down` avant tout `gtest`.
- **Jamais de `@Transactional` sur un handler** : la transaction appartient au bus.
- **Toute exception métier hérite de `RuntimeException`**, et son message est **affichable tel
  quel** à l'utilisateur, en français.
- **Langue :** code, commentaires, Javadoc et **noms de méthodes de test** en anglais ; messages
  d'exception métier et messages de commit en français.
- **Commentaires :** l'exception, jamais l'habitude. Trois lignes est un plafond.
- **Formatage :** `make format-back` avant tout commit. Ne pas se battre avec
  palantir-java-format.
- **Commits :** préfixe conventionnel en minuscule, description en français, un commit par tâche,
  tests verts.
- **Aucun ADR n'est écrit dans ce plan.** Les deux points de conception ci-dessus pourraient en
  appeler un ; les règles du dépôt interdisent de le rédiger sans accord préalable du
  propriétaire. Ils sont dits dans la PR.

## Fichiers

**Créés**

| Fichier | Responsabilité |
|---|---|
| `…/knowledge/domain/event/DocumentContentReplaced.java` | Le fait : ce document a un contenu neuf |
| `…/knowledge/application/command/ReplaceDocumentContent.java` | La commande, `toString()` redéfini (elle transporte des octets) |
| `…/knowledge/application/command/ReplaceDocumentContentHandler.java` | Le court-circuit, l'écrasement, le retour en `PENDING`, l'annonce |
| `…/knowledge/infrastructure/web/ReplaceDocumentContentController.java` | `PUT /api/documents/{id}` |
| `src/test/…/knowledge/domain/entity/DocumentReplacementTest.java` | Les invariants de `Document.replaceContent` |
| `src/test/…/knowledge/infrastructure/web/ReplaceDocumentContentControllerTest.java` | Les scénarios de la route, de bout en bout |

**Modifiés**

| Fichier | Modification |
|---|---|
| `…/knowledge/domain/entity/Document.java` | `replaceContent(filename, format, checksum, sizeBytes)` |
| `…/knowledge/domain/port/DocumentStorage.java` | `replace(documentId, content)`, qui écrase |
| `…/knowledge/infrastructure/storage/S3DocumentStorage.java` | Son implémentation |
| `…/knowledge/infrastructure/messaging/KnowledgeMessagingConfiguration.java` | Le nouvel événement déclaré |
| `…/knowledge/infrastructure/messaging/KnowledgeEventListener.java` | Un `@RabbitHandler` de plus |
| `src/test/…/knowledge/infrastructure/storage/S3DocumentStorageTest.java` | L'écrasement |
| `CLAUDE.md` | Le récit du flux, la liste des événements |

---

### Task 1: Le domaine sait remplacer le contenu d'un document

**Files:**
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/Document.java`
- Create: `src/test/java/xyz/sterenn/secondbrain/knowledge/domain/entity/DocumentReplacementTest.java`

**Interfaces:**
- Consomme : rien.
- Produit : `void Document.replaceContent(String filename, DocumentFormat format, Checksum checksum, long sizeBytes)`
  — consommé par la tâche 3.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `DocumentReplacementTest`, test unitaire pur (aucun Spring, aucun Testcontainers) :

```java
package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

class DocumentReplacementTest {

    private static final Checksum FIRST = Checksum.of("premier".getBytes());
    private static final Checksum SECOND = Checksum.of("second".getBytes());

    private static Document aDocument() {
        Document document = Document.upload(UUID.randomUUID(), "rapport.pdf", DocumentFormat.PDF, FIRST, 12L);
        document.markTextExtracted();
        document.markIndexed();
        return document;
    }

    @Test
    void carries_the_new_content_and_goes_back_to_pending() {
        Document document = aDocument();

        document.replaceContent("rapport-v2.pdf", DocumentFormat.PDF, SECOND, 34L);

        assertThat(document.getFilename()).isEqualTo("rapport-v2.pdf");
        assertThat(document.getChecksum()).isEqualTo(SECOND);
        assertThat(document.getSizeBytes()).isEqualTo(34L);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void clears_the_failure_reason_of_a_previous_attempt() {
        Document document = aDocument();
        document.markProcessingFailed("Ce document ne contient aucun texte exploitable.");

        document.replaceContent("rapport.pdf", DocumentFormat.PDF, SECOND, 34L);

        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void accepts_a_content_of_another_format() {
        Document document = aDocument();

        document.replaceContent("rapport.md", DocumentFormat.MARKDOWN, SECOND, 34L);

        assertThat(document.getFormat()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void bounds_a_filename_longer_than_the_column() {
        Document document = aDocument();
        String tooLong = "n".repeat(Document.MAX_FILENAME_LENGTH + 10) + ".pdf";

        document.replaceContent(tooLong, DocumentFormat.PDF, SECOND, 34L);

        assertThat(document.getFilename()).hasSize(Document.MAX_FILENAME_LENGTH);
    }

    @Test
    void rejects_an_empty_content() {
        Document document = aDocument();

        assertThatThrownBy(() -> document.replaceContent("rapport.pdf", DocumentFormat.PDF, SECOND, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_filename() {
        Document document = aDocument();

        assertThatThrownBy(() -> document.replaceContent("  ", DocumentFormat.PDF, SECOND, 34L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.entity.DocumentReplacementTest"
```

Attendu : **échec de compilation**, `cannot find symbol: method replaceContent(...)`.

- [ ] **Step 3: Écrire la méthode**

`Document.upload` porte déjà les trois contrôles (propriétaire, nom, taille) et le bornage du
nom. Les extraire dans deux méthodes privées statiques plutôt que les recopier, puis ajouter la
méthode d'instance à côté de `markIndexed` :

```java
    public void replaceContent(String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        this.filename = boundedFilename(filename);
        this.format = format;
        this.checksum = checksum;
        this.sizeBytes = requirePositive(sizeBytes);
        this.status = DocumentStatus.PENDING;
        this.errorMessage = null;
    }
```

`boundedFilename(String)` refuse le nom vide, le `trim`, et le tronque à
`MAX_FILENAME_LENGTH` ; `requirePositive(long)` refuse une taille nulle ou négative avec le
même message qu'aujourd'hui. `Document.upload` les appelle désormais toutes les deux : le
comportement d'aujourd'hui ne change pas, et `DocumentTest` doit rester vert sans être touché.

- [ ] **Step 4: Vérifier**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.domain.entity.*"
```

Attendu : vert, `DocumentTest` compris.

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/entity/Document.java src/test/java/xyz/sterenn/secondbrain/knowledge/domain/entity/DocumentReplacementTest.java
git commit
```

Message : `feat: un document sait remplacer son contenu et repartir en attente`

---

### Task 2: Le stockage sait écraser un original

**Files:**
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/DocumentStorage.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/storage/S3DocumentStorage.java`
- Modify: `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/storage/S3DocumentStorageTest.java`

**Interfaces:**
- Consomme : rien.
- Produit : `void DocumentStorage.replace(UUID documentId, byte[] content)` — consommé par la
  tâche 3.

- [ ] **Step 1: Écrire les tests qui échouent**

Ajouter à `S3DocumentStorageTest` (sans réordonner ce qui s'y trouve, et en respectant son
`@AfterEach` de nettoyage) :

```java
    @Test
    void replaces_the_original_of_a_document() {
        UUID documentId = UUID.randomUUID();
        documentStorage.store(documentId, "premier".getBytes(StandardCharsets.UTF_8));

        documentStorage.replace(documentId, "second".getBytes(StandardCharsets.UTF_8));

        assertThat(documentStorage.read(documentId)).contains("second".getBytes(StandardCharsets.UTF_8));
    }

    // A replacement does not check what it replaces: a row whose original has gone missing must
    // be repairable, not left without one.
    @Test
    void writes_an_original_that_was_not_there() {
        UUID documentId = UUID.randomUUID();

        documentStorage.replace(documentId, "second".getBytes(StandardCharsets.UTF_8));

        assertThat(documentStorage.read(documentId)).contains("second".getBytes(StandardCharsets.UTF_8));
    }
```

Reprendre exactement les conventions du fichier (nom du champ injecté, imports déjà présents) ;
si le test ne garde pas trace des identifiants écrits pour son nettoyage, vérifier que
`emptyTheOriginals` vide bien tout le bucket — c'est le cas aujourd'hui.

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.storage.S3DocumentStorageTest"
```

Attendu : **échec de compilation**, `cannot find symbol: method replace(...)`.

- [ ] **Step 3: Déclarer le port**

Dans `DocumentStorage`, ajouter la méthode et **compléter le Javadoc de l'interface** : il dit
aujourd'hui « a write never overwrites », ce qui devient faux pour la moitié de ses méthodes.

```java
    /**
     * Overwrites the original of a document, unlike {@link #store}, whose refusal to overwrite
     * guards against a handler calling it twice. Writes an original that was not there.
     */
    void replace(UUID documentId, byte[] content);
```

- [ ] **Step 4: Implémenter dans l'adapter**

Dans `S3DocumentStorage`, à côté de `store` :

```java
    @Override
    public void replace(UUID documentId, byte[] content) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key(documentId)).build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw unavailable("replaced", e);
        }
    }
```

Pas de vérification d'existence : `PutObject` écrase, c'est exactement ce qu'on veut.

- [ ] **Step 5: Vérifier**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.storage.*"
```

- [ ] **Step 6: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/domain/port/DocumentStorage.java src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/storage/S3DocumentStorage.java src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/storage/S3DocumentStorageTest.java
git commit
```

Message : `feat: le stockage des originaux sait en écraser un`

---

### Task 3: L'événement, la commande et son handler

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/domain/event/DocumentContentReplaced.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/ReplaceDocumentContent.java`
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/application/command/ReplaceDocumentContentHandler.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeMessagingConfiguration.java`
- Modify: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/messaging/KnowledgeEventListener.java`

**Interfaces:**
- Consomme : `Document.replaceContent`, `DocumentStorage.replace`.
- Produit : la commande `ReplaceDocumentContent(UUID ownerId, UUID documentId, String filename, byte[] content)`
  — consommée par la tâche 4.

- [ ] **Step 1: L'événement**

`DocumentContentReplaced`, copie conforme de `DocumentUploaded` — mêmes trois composants, mêmes
`Objects.requireNonNull`. Son nom simple donne la clé de routage
`knowledge.document-content.replaced`, que le binding `knowledge.#` couvre déjà : **aucune
configuration de binding à ajouter**.

- [ ] **Step 2: La commande**

```java
public record ReplaceDocumentContent(UUID ownerId, UUID documentId, String filename, byte[] content)
        implements Command {

    @Override
    public String toString() {
        return "ReplaceDocumentContent[ownerId=" + ownerId + ", documentId=" + documentId + ", filename=" + filename
                + ", content=" + content.length + " octets]";
    }
}
```

- [ ] **Step 3: Le handler**

L'ordre des étapes reprend celui d'`UploadDocumentHandler` — original après la ligne,
publication en dernier — et ajoute le court-circuit en tête :

```java
@Component
public class ReplaceDocumentContentHandler implements CommandHandler<ReplaceDocumentContent> {

    // constructeur : DocumentRepository, DocumentStorage, DomainEventPublisher, Clock

    @Override
    public void handle(ReplaceDocumentContent command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        DocumentFormat format = DocumentFormat.fromFilename(command.filename());
        Checksum checksum = Checksum.of(command.content());

        // The most frequent case of a synchronisation, and it must cost nothing: no write, no
        // vectorisation, no announcement.
        if (checksum.equals(document.getChecksum())) {
            return;
        }

        documentRepository
                .findByOwnerIdAndChecksum(command.ownerId(), checksum)
                .ifPresent(other -> {
                    throw new DuplicateDocumentException(other.getId());
                });

        document.replaceContent(command.filename(), format, checksum, command.content().length);
        documentRepository.save(document);

        // The file after the row: see ADR-0020.
        documentStorage.replace(document.getId(), command.content());

        domainEventPublisher.publish(
                new DocumentContentReplaced(document.getId(), document.getOwnerId(), clock.instant()));
    }
}
```

`DocumentFormat.fromFilename` lève `UnsupportedDocumentFormatException` **avant** le calcul de
l'empreinte : un `.png` est refusé, même s'il portait par miracle le contenu du PDF en place.

- [ ] **Step 4: Déclarer l'événement et le traiter**

Dans `KnowledgeMessagingConfiguration`, ajouter `DocumentContentReplaced.class` à la liste du
`DomainEventRegistration`. **Sans cette ligne, la désérialisation refuse le message** : le
mapper est en `TypePrecedence.TYPE_ID`, et un nom absent de la table est confronté aux paquets
de confiance.

Dans `KnowledgeEventListener`, un `@RabbitHandler` de plus, qui fait exactement ce que fait
celui de `DocumentUploaded` :

```java
    /** A replaced content re-enters the pipeline exactly where an upload does — same command, same repairs. */
    @RabbitHandler
    public void on(DocumentContentReplaced event) {
        extract(event.documentId(), event.ownerId());
    }
```

et extraire le corps commun aux deux en une méthode privée `extract(UUID documentId, UUID ownerId)`
plutôt que le recopier.

- [ ] **Step 5: Vérifier**

```bash
gtest compileJava
gtest test --tests "xyz.sterenn.secondbrain.knowledge.*"
```

- [ ] **Step 6: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge
git commit
```

Message : `feat: le remplacement du contenu d'un document relance le pipeline`

---

### Task 4: La route `PUT /api/documents/{id}`

**Files:**
- Create: `src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/ReplaceDocumentContentController.java`
- Create: `src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web/ReplaceDocumentContentControllerTest.java`

**Interfaces:**
- Consomme : `ReplaceDocumentContent`.
- Produit : la route.

- [ ] **Step 1: Écrire les tests qui échouent**

Modeler le fichier sur `DeleteDocumentControllerTest` : `@Import({TestcontainersConfiguration.class,
RecordingNotificationSenderConfiguration.class})`, `@SpringBootTest`, `@AutoConfigureMockMvc`,
`@Transactional`, `AccountFixture.registerVerified`, `KnowledgeFixture.token`, et le
`@AfterEach` qui vide le bucket.

Un `PUT` multipart s'écrit `multipart(HttpMethod.PUT, "/api/documents/{id}", id)` — le
`MockMvcRequestBuilders.multipart` par défaut est un `POST`.

Les scénarios, un par attendu du ticket, plus les refus :

| Méthode de test | Ce qu'elle vérifie |
|---|---|
| `replaces_the_content_of_a_document` | `200`, l'empreinte en base a changé, le statut est repassé à `PENDING`, l'original relu par le port porte les nouveaux octets |
| `does_nothing_when_the_content_is_strictly_identical` | `200`, le statut **n'a pas** bougé (le document était `READY`, il l'est resté) |
| `rejects_a_content_already_held_by_another_document` | `409`, et le corps porte `existingDocumentId` |
| `rejects_an_unsupported_format` | `415` |
| `rejects_an_empty_file` | `422`, champ `file` |
| `refuses_to_replace_the_document_of_another_account` | `404` — le document d'autrui est introuvable, jamais interdit |
| `refuses_an_unknown_document` | `404` |
| `refuses_an_anonymous_request` | `401` |

**Rappel de la règle des tests :** dans un test `@Transactional`, un appel HTTP refusé doit être
le **dernier** du test — l'exception métier marque la transaction englobante « rollback-only ».
Ce qu'il reste à vérifier après un refus se lit par le port, pas par une seconde requête.

Pour `does_nothing_when_the_content_is_strictly_identical`, amener le document à `READY` sans
le worker : dispatcher `ExtractDocumentText` puis `IndexDocumentText` demanderait Ollama.
Passer plutôt par le port — relire le document, appeler `markTextExtracted()` puis
`markIndexed()`, sauver — et vérifier ensuite que le statut est toujours `READY`.

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.ReplaceDocumentContentControllerTest"
```

Attendu : `404` partout — la route n'existe pas.

- [ ] **Step 3: Écrire le contrôleur**

Mono-route, nommé par l'intention. Il reprend mot pour mot les refus d'`UploadDocumentController`
— fichier vide en `422` sur le champ `file`, format en `415`, doublon en `409` avec
`DuplicateDocumentResponse` — et ajoute le `404` de `DeleteDocumentController`. Les deux
`@ExceptionHandler` (`MaxUploadSizeExceededException`, `JwtSubject.UnreadableSubjectException`)
sont recopiés : ils appartiennent à la route, c'est le prix assumé de l'absence de
`@RestControllerAdvice`.

Le succès rend `200` **sans corps** : comme au dépôt, rien du document n'est à exposer, et
`GET /api/documents` rend l'état complet.

- [ ] **Step 4: Vérifier**

```bash
gtest test --tests "xyz.sterenn.secondbrain.knowledge.infrastructure.web.*"
```

- [ ] **Step 5: Formater et committer**

```bash
make format-back
git add src/main/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web src/test/java/xyz/sterenn/secondbrain/knowledge/infrastructure/web
git commit
```

Message : `feat: PUT /api/documents/{id} remplace le contenu d'un document`

---

### Task 5: La suite complète, puis la documentation

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Lancer toute la suite**

```bash
docker compose down
gtest build
```

Attendu : vert, formatage compris — `spotlessCheck` est accroché à `check`, donc à `build`.

- [ ] **Step 2: Écrire le récit du flux**

Dans `CLAUDE.md`, section « Architecture », ajouter une sous-section
**« Le flux du remplacement du contenu d'un document »** après « Le flux du dépôt d'un
document ». Elle doit dire, sans recopier le code :

- `PUT /api/documents/{id}` en multipart, `200` sans corps, mêmes refus qu'au dépôt plus le
  `404` du document introuvable ;
- **l'identité ne bouge pas** : même identifiant, même place dans la liste ; seuls le nom, le
  format, l'empreinte, la taille et le statut changent ;
- **le court-circuit sur empreinte identique**, et pourquoi il compte : c'est le cas nominal
  d'une synchronisation, et chaque revectorisation inutile immobilise le worker plusieurs
  minutes ;
- `DocumentContentReplaced` et pourquoi ce n'est pas `DocumentUploaded` republié ;
- le fait que le pipeline n'a **pas** été modifié : il efface déjà texte et extraits avant
  d'écrire, parce qu'AMQP livre au moins une fois — c'est la troisième fois qu'un ticket
  s'appuie sur cette idempotence.

Compléter aussi la section « Persistance » d'une phrase : `DocumentStorage` porte désormais
`replace`, qui écrase là où `store` refuse de le faire, et la promesse « ne participe à aucune
transaction » d'ADR-0020 vaut pour lui aussi.

- [ ] **Step 3: Committer**

```bash
git add CLAUDE.md
git commit
```

Message : `docs: documente le remplacement du contenu d'un document`
