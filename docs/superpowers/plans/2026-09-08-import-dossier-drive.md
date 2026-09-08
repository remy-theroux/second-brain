# Importer les fichiers d'un dossier surveillé — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Balayer un dossier surveillé, télécharger ce qui est exploitable, et le confier au
pipeline d'ingestion déjà en place **sans le modifier**. Un document importé est traité exactement
comme un dépôt manuel.

**Architecture:** Trois pièces, et la première est celle qui compte.

**1. La boucle vit hors des bus et sans transaction.** Le ticket l'impose — « un dossier de cinq
cents fichiers ne tient pas dans un aller-retour » — et l'exigence « les documents déjà entrés
restent dans ma base » quand Drive tombe **interdit** une transaction unique pour tout l'import.
`DriveFolderImporter` est donc un composant d'`application/`, comme `ConversationAgent`, appelé
par le listener et non par un handler de commande : un handler tournerait dans la transaction du
bus, ce qu'on veut précisément éviter. Il dispatche **une commande par fichier**, donc une
transaction courte chacune.

`POST /api/drive/watched-folders/{id}/import` rend `202` et publie `DriveFolderImportRequested`.
Le worker reçoit, balaie, télécharge, dispatche.

**2. L'unicité de contenu s'assouplit — le seul endroit où ce lot change une règle existante.**
`ImportDriveFileHandler`, dans cet ordre :

1. un document existe déjà pour `(ownerId, driveFileId)` → **rien**, c'est un second import ;
2. sinon, un document existe pour `(ownerId, checksum)` → on lui **rattache** sa provenance Drive,
   sans créer de second document et sans revectoriser ;
3. sinon → création avec provenance, original stocké, `DocumentUploaded` publié.

**`UploadDocument` n'est donc pas réutilisable telle quelle**, contrairement au pointeur du ticket :
elle lève `DuplicateDocumentException` au cas 2. Une commande distincte est plus honnête qu'un
drapeau `boolean fromDrive` glissé dans l'ancienne, qui ferait diverger deux comportements sous un
seul nom.

**3. La provenance entre dans `knowledge_documents`.** Quatre colonnes, dont une qui ne sert
qu'à DRIVE-5 et qui est posée **maintenant** : `drive_modified_time`. L'ajouter plus tard
obligerait à rebalayer tout le Drive pour les documents déjà importés.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 · Spring AMQP · Flyway · PostgreSQL ·
JUnit 5 + AssertJ + Testcontainers. Aucune dépendance nouvelle.

**Ticket:** DRIVE-3 — Importer les fichiers d'un dossier surveillé. Prérequis : DRIVE-1, DRIVE-2.

## Global Constraints

Les mêmes que DRIVE-1 et DRIVE-2, plus :

- **Le pipeline d'ingestion ne se modifie pas.** Si une ligne de `ExtractDocumentTextHandler` ou
  d'`IndexDocumentTextHandler` change, c'est que la conception a dérapé.
- **Le contenu transite entièrement en mémoire** (ADR-0021), comme au dépôt manuel.
- **Aucun ADR n'est écrit.** L'assouplissement de l'unicité en appellera un ; il est signalé dans
  la PR.

## Fichiers

**Créés — domaine**

| Fichier | Responsabilité |
|---|---|
| `…/domain/valueobject/DocumentSource.java` | `MANUAL` / `GOOGLE_DRIVE` |
| `…/domain/valueobject/DriveProvenance.java` | Identifiant Drive, lien d'ouverture, `modifiedTime` |
| `…/domain/valueobject/DriveFile.java` | Un fichier tel que `files.list` le rend |
| `…/domain/ImportPolicy.java` | Le plafond de taille, règle du domaine |
| `…/domain/entity/DriveImportRejection.java` | Un fichier écarté et son motif |
| `…/domain/port/GoogleDriveFiles.java` | Le balayage récursif et le téléchargement |
| `…/domain/port/DriveImportRejectionRepository.java` | |
| `…/domain/event/DriveFolderImportRequested.java` | |

**Créés — application**

| Fichier | Responsabilité |
|---|---|
| `…/application/drive/DriveFolderImporter.java` | La boucle, hors des bus |
| `…/application/command/ImportDriveFile.java` + handler | Les trois cas, en une transaction courte |
| `…/application/command/RequestDriveFolderImport.java` + handler | Publie l'événement |
| `…/application/command/RecordDriveImportOutcome.java` + handler | Le bilan du dossier |

**Créés — infrastructure**

| Fichier | Responsabilité |
|---|---|
| `…/infrastructure/drive/GoogleDriveFilesAdapter.java` | `files.list` récursif, `files.get?alt=media` |
| `…/infrastructure/web/RequestDriveFolderImportController.java` | `POST …/{id}/import` |
| `…/infrastructure/persistence/…` | Le repository des rejets |
| `V16__add_knowledge_documents_provenance.sql` | Les quatre colonnes et l'unicité |
| `V17__create_knowledge_drive_import_rejections.sql` | Les rejets et le bilan du dossier |

**Modifiés**

| Fichier | Modification |
|---|---|
| `…/domain/entity/Document.java` | `importedFromDrive(...)`, `attachTo(DriveProvenance)`, les getters |
| `…/domain/entity/WatchedFolder.java` | `lastImportAt`, `lastImportStatus`, `lastImportError` |
| `…/domain/port/DocumentRepository.java` | `findByOwnerIdAndDriveFileId` |
| `…/infrastructure/messaging/KnowledgeEventListener.java` | Un `@RabbitHandler` de plus |
| `…/infrastructure/messaging/KnowledgeMessagingConfiguration.java` | L'événement déclaré |
| `…/application/query/DocumentView.java`, `DocumentDetailView.java` | La provenance |
| `CLAUDE.md` | Le récit du flux |

---

### Task 1: La provenance d'un document

**Files:** `DocumentSource`, `DriveProvenance`, `Document`, la migration `V16`,
`DocumentRepository.findByOwnerIdAndDriveFileId` + adapter, `DocumentProvenanceTest`

- [ ] **Step 1: Écrire les tests qui échouent**

`DocumentProvenanceTest`, unitaire pur :

| Test | Ce qu'il vérifie |
|---|---|
| `a_manually_uploaded_document_has_no_drive_provenance` | `Document.upload` rend `MANUAL` et une provenance vide |
| `an_imported_document_carries_its_drive_file_and_link` | `Document.importedFromDrive` |
| `attaching_a_drive_provenance_leaves_the_content_untouched` | Empreinte, statut et extraits inchangés — c'est le cas 2 du handler |
| `refuses_to_attach_a_provenance_to_a_document_that_already_has_one` | Un document ne vient pas de deux fichiers Drive |

- [ ] **Step 2: La migration**

```sql
ALTER TABLE knowledge_documents
    ADD COLUMN source                VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN drive_file_id         VARCHAR(255),
    ADD COLUMN drive_web_view_link   TEXT,
    ADD COLUMN drive_modified_time   TIMESTAMP WITH TIME ZONE;

ALTER TABLE knowledge_documents
    ADD CONSTRAINT uq_knowledge_documents_owner_drive_file UNIQUE (owner_id, drive_file_id);
```

Reprendre la forme de `V13` et `V5` (majuscules, contraintes nommées, en-tête en français).
**Les `NULL` de Postgres étant distincts entre eux**, l'unicité ne gêne pas les documents déposés
à la main, qui sont tous à `NULL`. Le `DEFAULT 'MANUAL'` vaut pour les lignes déjà en base ; le
dire dans l'en-tête.

**`drive_modified_time` ne sert à rien avant DRIVE-5**, et c'est délibéré : un Google Doc s'y
compare, jamais par son empreinte (DRIVE-4), et l'ajouter plus tard demanderait de rebalayer tout
le Drive pour les documents déjà importés.

- [ ] **Step 3: Le domaine, puis vérifier, formater, committer**

Message : `feat: un document sait d'où il vient`

---

### Task 2: Balayer et télécharger

**Files:** `DriveFile`, `GoogleDriveFiles`, `GoogleDriveFilesAdapter`, `ImportPolicy`,
le bouchon de Drive étendu

- [ ] **Step 1: Étendre le bouchon**

`FakeGoogleDriveConfiguration` (DRIVE-2) sait déjà rendre une arborescence de dossiers. Lui
ajouter des **fichiers** par dossier, avec `id`, `name`, `mimeType`, `size`, `webViewLink`,
`modifiedTime`, et un contenu binaire programmable. Il doit savoir devenir injoignable en cours
de balayage — c'est le dernier scénario du ticket.

- [ ] **Step 2: Écrire les tests qui échouent**

`DriveFolderScanTest`, sur le port :

| Test | Ce qu'il vérifie |
|---|---|
| `walks_the_files_of_a_folder` | |
| `walks_the_files_of_its_subfolders` | Import récursif |
| `pages_through_a_folder_of_more_than_a_hundred_files` | `nextPageToken` suivi — sans quoi un gros dossier perd la moitié de son contenu **en silence** |
| `ignores_a_file_whose_format_is_not_supported` | Une image n'est pas une erreur |

- [ ] **Step 3: L'adapter**

`files.list` avec `q='<parent>' in parents and trashed=false`,
`fields=files(id,name,mimeType,size,webViewLink,modifiedTime),nextPageToken`, et la descente
récursive dans les dossiers rencontrés. Le téléchargement par `files.get?alt=media`.

**`size` est absent des Google Docs natifs** et arrive en `String` dans le JSON de Drive, pas en
nombre. Le lire comme tel et le convertir, plutôt que de laisser Jackson échouer.

**Le plafond se contrôle avant le téléchargement** : `files.list` rend déjà `size`, et payer le
transfert d'un fichier qu'on va refuser n'a aucun sens. `ImportPolicy.MAX_FILE_SIZE` vaut la même
chose que le dépôt manuel (20 Mo) — deux chemins d'entrée dans la même base ne doivent pas avoir
deux plafonds.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: balayer récursivement un dossier Drive et en télécharger les fichiers`

---

### Task 3: Importer un fichier

**Files:** `ImportDriveFile` + handler, `DriveImportRejection` + son repository, `V17`,
`ImportDriveFileTest`

- [ ] **Step 1: Écrire les tests qui échouent**

| Méthode de test | Scénario du ticket |
|---|---|
| `imports_a_file_that_the_base_does_not_hold` | Le document apparaît, en attente, son original est conservé |
| `attaches_the_drive_source_to_a_document_already_uploaded_by_hand` | **Aucun second document**, et le premier porte maintenant sa provenance |
| `does_nothing_on_a_second_import_of_the_same_drive_file` | |
| `re_imports_nothing_when_only_the_name_changed` | L'empreinte décide, pas le nom |
| `rejects_a_file_beyond_the_ceiling_with_a_readable_reason` | Le motif est consultable |

**Attention au cas 2 :** rattacher une provenance ne doit **rien** relancer. Pas de
`DocumentUploaded`, pas de revectorisation — le contenu n'a pas bougé. Un test doit l'observer,
sans quoi la première synchronisation d'un Drive revectoriserait toute la base.

- [ ] **Step 2: La migration `V17`**

La table des rejets, plus les trois colonnes de bilan sur `knowledge_drive_watched_folders`
(`last_import_at`, `last_import_status`, `last_import_error`). Les rejets cascadent sur le dossier
surveillé, et sont **remplacés** à chaque import, jamais cumulés : un fichier réparé doit quitter
la liste.

- [ ] **Step 3: Le handler, puis vérifier, formater, committer**

Message : `feat: importer un fichier Drive, ou le rattacher au document qui le porte déjà`

---

### Task 4: La boucle, l'événement et la route

**Files:** `DriveFolderImporter`, `DriveFolderImportRequested`, `RequestDriveFolderImport` +
handler, `RecordDriveImportOutcome` + handler, le contrôleur, le listener, la configuration
messaging, `DriveFolderImportTest`

- [ ] **Step 1: Écrire les tests qui échouent**

`DriveFolderImportTest`, en `@ActiveProfiles("worker")` sur le modèle de
`KnowledgeEventListenerTest` :

| Méthode de test | Scénario du ticket |
|---|---|
| `imports_every_supported_file_of_a_watched_folder` | PDF et DOCX entrent, en attente de traitement |
| `imports_the_files_of_a_subfolder_too` | |
| `leaves_an_unsupported_file_out_without_failing_the_import` | Seul le PDF entre, l'import se termine sans échec |
| `keeps_the_documents_already_imported_when_the_drive_becomes_unreachable` | **Le scénario qui justifie toute l'architecture** |
| `records_the_reason_of_a_file_left_out` | |

- [ ] **Step 2: La boucle**

`DriveFolderImporter` n'est **pas** un `CommandHandler`. Il est appelé par le listener, hors
transaction, et dispatche `ImportDriveFile` par fichier. Une `GoogleDriveUnavailableException` en
cours de balayage **arrête la boucle** et fait écrire le bilan en échec par une seconde commande —
même dispositif qu'ADR-0028, et pour la même raison : le bilan écrit dans une transaction annulée
disparaîtrait avec elle.

**Le listener doit tenir la livraison AMQP pendant tout l'import**, qui peut durer. `compose.yaml`
pose déjà `consumer_timeout` à deux heures pour la vectorisation ; vérifier que ça suffit et le
dire dans la documentation si un dossier de cinq cents fichiers peut le dépasser.

- [ ] **Step 3: La route**

`POST /api/drive/watched-folders/{id}/import` → `202` sans corps : le travail n'est pas fait quand
la réponse part, et c'est exactement ce que `202` dit. `404` sur un dossier inconnu ou d'autrui.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: importer les fichiers d'un dossier surveillé, dans le worker`

---

### Task 5: La suite complète, puis la documentation

- [ ] `docker compose down` puis `gtest build`.
- [ ] `CLAUDE.md` : une sous-section « Le flux de l'import d'un dossier Drive » qui dit la boucle
  hors des bus et **pourquoi**, la commande par fichier et sa transaction courte,
  l'assouplissement de l'unicité et ses trois cas, la provenance et la colonne posée d'avance
  pour DRIVE-5, le plafond contrôlé avant le téléchargement, les formats ignorés en silence, la
  pagination sans laquelle un gros dossier se perd à moitié. Compléter « Persistance ».
- [ ] Message : `docs: documente l'import d'un dossier Drive`
