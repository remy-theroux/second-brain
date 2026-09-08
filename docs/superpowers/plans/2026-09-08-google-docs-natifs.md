# Ingérer les Google Docs natifs par export DOCX — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un Google Doc d'un dossier surveillé se retrouve dans la liste avec ses sections,
**indistinguable d'un DOCX déposé à la main**.

**Architecture:** Le plus petit ticket de la série — DRIVE-3 a déjà la boucle, il n'y a qu'un
`mimeType` de plus à traiter. Trois pièces :

1. **L'export.** `application/vnd.google-apps.document` n'a pas de binaire à télécharger : il
   s'exporte, par `files.export`, en
   `application/vnd.openxmlformats-officedocument.wordprocessingml.document`. Le fichier produit
   prend le nom du Doc suivi de `.docx`, et pour tout l'aval c'est un DOCX ordinaire — même
   extracteur, même découpage, même typologie (ADR-0029).

   **DOCX et non PDF**, et ce n'est pas un détail : les styles `Heading` y sont préservés, donc
   l'extracteur DOCX en tire de vraies sections. Un export PDF ferait deviner les titres à la
   taille de police (ADR-0027) là où l'information est disponible.

2. **Le plafond de Google, qui arrive trop tard pour être anticipé.** Google refuse d'exporter
   au-delà de **10 Mo**, et `files.list` ne rend **aucun `size` pour un Doc natif** : le contrôle
   de plafond de DRIVE-3, qui écarte avant de télécharger, ne peut pas jouer. C'est donc un rejet
   **a posteriori**, sur l'échec de l'appel, avec motif — et il ne fait pas tomber l'import.

3. **Le piège central : l'export n'est pas déterministe.** Le DOCX produit par Google embarque
   des métadonnées et des horodatages dans son archive ZIP ; deux exports d'un Doc **inchangé**
   donnent deux empreintes différentes. La détection de changement d'un Google Doc s'appuie donc
   sur le `modifiedTime` de Drive, **jamais sur le checksum**. Sans cette précaution, chaque
   synchronisation revectoriserait tous les Docs — le contraire exact de l'invariant de DRIVE-5.

   Conséquence pour ce ticket : le court-circuit sur `driveFileId` de DRIVE-3 doit précéder tout
   calcul d'empreinte, et un test doit le **prouver** avec un bouchon qui rend deux exports
   différents pour un Doc dont le `modifiedTime` n'a pas bougé.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 · Apache POI (déjà en place, non modifié) ·
JUnit 5 + AssertJ + Testcontainers. Aucune dépendance nouvelle, **aucune migration** —
`drive_modified_time` a été posée dès DRIVE-3 précisément pour ça.

**Ticket:** DRIVE-4 — Ingérer les Google Docs natifs par export DOCX. Prérequis : DRIVE-3.

## Global Constraints

Les mêmes que DRIVE-3, plus :

- **`knowledge/infrastructure/extraction/` ne se touche pas.** L'extracteur DOCX est réutilisé
  **sans modification** : si une de ses lignes change, c'est que la conception a dérapé.
- **Sheets, Slides, Drawings, Forms sont hors périmètre** et ignorés **en silence**, comme une
  image — pas dans la table des rejets. Chacun demanderait sa typologie et ses tables (ADR-0030).
- **Aucun ADR n'est écrit.**

## Fichiers

**Créés**

| Fichier | Responsabilité |
|---|---|
| `…/domain/valueobject/GoogleWorkspaceType.java` | Ce que Drive appelle un Doc, une feuille, une présentation — et lequel s'exporte |
| `src/test/…/knowledge/infrastructure/web/GoogleDocImportTest.java` | Les sept scénarios du ticket |

**Modifiés**

| Fichier | Modification |
|---|---|
| `…/domain/port/GoogleDriveFiles.java` | `export(accessToken, fileId)` |
| `…/infrastructure/drive/GoogleDriveFilesAdapter.java` | `files.export`, et le plafond de 10 Mo rendu comme un rejet |
| `…/application/drive/DriveFolderImporter.java` | Le `mimeType` de plus, et le nom qui gagne `.docx` |
| `…/domain/ImportPolicy.java` | Le plafond d'export de Google, distinct de celui du dépôt |
| `src/test/…/knowledge/FakeGoogleDriveConfiguration.java` | Des Docs natifs programmables, dont un export non déterministe |
| `CLAUDE.md` | Le récit |

---

### Task 1: Reconnaître un Google Doc, et lui seul

**Files:** `GoogleWorkspaceType`, `DriveFolderImporter`, `GoogleWorkspaceTypeTest`

**Interfaces:**
- Consomme : `DriveFile` (DRIVE-3).
- Produit : la décision « ce fichier s'exporte / se télécharge / s'ignore ».

- [ ] **Step 1: Écrire les tests qui échouent**

`GoogleWorkspaceTypeTest`, unitaire pur :

| Test | Ce qu'il vérifie |
|---|---|
| `exports_a_google_document_as_docx` | Le seul type exporté, et son type MIME de sortie |
| `ignores_a_spreadsheet_a_presentation_and_a_drawing` | Trois `mimeType` de Workspace, tous ignorés |
| `is_not_concerned_by_an_ordinary_binary` | Un `application/pdf` n'est pas un type Workspace |

- [ ] **Step 2: Le nom du fichier produit**

Un Google Doc n'a **pas d'extension** dans son nom Drive. `DocumentFormat.fromFilename` refuserait
donc « Compte rendu du 3 mars ». Le nom du document créé est le nom du Doc **suivi de `.docx`**,
et c'est ce nom-là qui entre en base — c'est aussi celui que l'utilisateur verra, et celui qui
entre dans le préfixe de contextualisation des extraits.

Un test doit le poser explicitement : un Doc nommé `Notes de réunion` donne un document
`Notes de réunion.docx`, de format `DOCX`.

**Attention au Doc dont le nom porte déjà `.docx`** — c'est légal côté Drive. Ne pas produire
`fichier.docx.docx` : si le nom se termine déjà par l'extension, ne rien ajouter.

- [ ] **Step 3: Vérifier, formater, committer**

Message : `feat: reconnaître un Google Doc natif parmi les fichiers d'un dossier`

---

### Task 2: L'export, et son plafond

**Files:** `GoogleDriveFiles`, `GoogleDriveFilesAdapter`, `ImportPolicy`,
`FakeGoogleDriveConfiguration`, `GoogleDriveFilesAdapterTest`

- [ ] **Step 1: Étendre le bouchon**

Des Google Docs programmables, avec leur `modifiedTime`, leur contenu exporté, et deux
comportements de panne : un export qui dépasse 10 Mo, et un export **non déterministe** — deux
appels successifs rendant deux tableaux d'octets différents pour un Doc inchangé. Ce dernier est
le sujet du test le plus important de ce ticket.

- [ ] **Step 2: Écrire les tests qui échouent**

| Test | Ce qu'il vérifie |
|---|---|
| `exports_a_google_document_as_a_docx_archive` | L'appel porte le bon type MIME de sortie |
| `reports_a_document_whose_export_exceeds_the_ceiling_of_google` | Un rejet avec motif, pas une panne |

- [ ] **Step 3: L'adapter**

`GET /drive/v3/files/{id}/export?mimeType=…`. Google rend `403` avec
`exportSizeLimitExceeded` au-delà de 10 Mo : le distinguer d'un `403` de droits, et ne lever le
rejet que pour celui-là. Un `403` de droits reste une `GoogleDriveUnavailableException` — confondre
les deux ferait passer un problème de permission pour un problème de taille, et l'utilisateur
chercherait au mauvais endroit.

`ImportPolicy` porte les **deux** plafonds : celui du dépôt (20 Mo, qui vaut pour les binaires
téléchargés) et celui de Google (10 Mo, subi et non choisi). Les nommer distinctement, et dire en
commentaire que le second n'est pas une décision du projet.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: exporter un Google Doc en DOCX, et écarter celui que Google refuse d'exporter`

---

### Task 3: Le trajet complet, et l'invariant du `modifiedTime`

**Files:** `GoogleDocImportTest`, `DriveFolderImporter`, `ImportDriveFile` + handler si besoin

- [ ] **Step 1: Écrire les tests qui échouent**

Les sept scénarios du ticket, un test chacun :

| Méthode de test | Scénario |
|---|---|
| `imports_a_google_document_of_a_watched_folder` | Il apparaît, en attente de traitement |
| `keeps_the_headings_of_the_google_document` | Son texte extrait est découpé selon ses titres — **le test qui justifie l'export DOCX plutôt que PDF** |
| `leaves_a_spreadsheet_out_without_failing_the_import` | |
| `leaves_a_presentation_out` | |
| `reports_a_document_too_large_to_export_without_failing_the_import` | Les autres fichiers entrent normalement |
| `marks_a_document_without_usable_text_as_failed` | Le plancher de 50 caractères d'ADR-0025, avec un motif qui nomme l'inexploitabilité |
| `re_imports_the_latest_version_of_a_document_modified_since` | |

Et **le test qui n'est pas dans le ticket mais qui en porte la contrainte centrale** :

| `does_not_re_ingest_a_google_document_whose_modified_time_has_not_changed` | Le bouchon rend **deux exports différents** pour un Doc inchangé, et rien ne doit être revectorisé |

Sans ce dernier, rien n'empêche une régression qui ferait revectoriser tout le Drive à chaque
synchronisation — et cette régression serait invisible, sauf à regarder la charge du worker.

- [ ] **Step 2: Le code, puis vérifier, formater, committer**

Message : `feat: les Google Docs d'un dossier surveillé entrent dans la base`

---

### Task 4: La suite complète, puis la documentation

- [ ] `docker compose down` puis `gtest build`, **une seule fois**.
- [ ] `CLAUDE.md` : compléter « Le flux de l'import d'un dossier Drive » d'un paragraphe sur les
  Google Docs, qui dit : l'export DOCX et **pourquoi pas PDF** (les styles `Heading` contre la
  taille de police d'ADR-0027) ; le nom qui gagne `.docx`, sans le doubler ; le plafond de 10 Mo
  **subi**, distingué d'un refus de droits ; les autres types Workspace ignorés en silence et
  pourquoi (ADR-0030) ; et surtout **l'export non déterministe**, donc la comparaison par
  `modifiedTime` et jamais par empreinte — avec la conséquence si on l'oublie : tout le Drive
  revectorisé à chaque tour.
- [ ] Message : `docs: documente l'ingestion des Google Docs natifs`
