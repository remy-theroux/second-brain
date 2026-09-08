# Refléter les changements du Drive — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ce que je lis dans Drive et ce que l'agent cite sont **toujours la même chose**. Je
modifie un fichier, et sans rien faire d'autre, l'agent cite sa nouvelle version.

**Architecture:** Une tâche planifiée dans le worker, `changes.list` et son jeton de page, et
**aucune commande nouvelle sauf une**.

`DriveSynchroniser` (dans `application/drive/`, à côté de `DriveFolderImporter`) tourne sur
horloge, lit les changements depuis le jeton conservé, et pour chacun décide d'un geste :

| Changement | Geste | Commande |
|---|---|---|
| Contenu modifié | Ré-ingestion sans changer d'identité | `ReplaceDocumentContent` (RAG-7) |
| Fichier ajouté dans un dossier surveillé | Import | `ImportDriveFile` (DRIVE-3) |
| Fichier supprimé ou mis à la corbeille | Retrait | `DeleteDocument` |
| Fichier sorti des dossiers surveillés | Retrait | `DeleteDocument` |
| Fichier renommé | Le nom change, **rien d'autre** | `RenameDocument` — **la seule commande neuve** |
| Fichier déplacé dans un sous-dossier surveillé | Rien | — |
| Rien n'a bougé | Rien | — |

**Pourquoi `RenameDocument` doit exister.** `ReplaceDocumentContent` court-circuite sur empreinte
identique et ne touche donc pas au nom — c'est exactement ce que RAG-7 a documenté (« le nom ne
suit que le contenu »). Aucune commande existante ne sait renommer sans revectoriser.

**Ce que le renommage laisse derrière lui, et qu'il faut dire.** Le nom entre dans le **préfixe de
contextualisation** des extraits (`Chunk.contextualised(filename)`), mais pas dans la colonne
`text`. Renommer sans revectoriser laisse donc les vecteurs calculés sous l'**ancien** nom. C'est
ce que le ticket demande (« ses extraits ne sont pas recalculés »), et c'est un écart assumé — mais
il nuance la promesse de RAG-5 selon laquelle « changer la forme du préfixe ne demandera que de
revectoriser ». À écrire dans `CLAUDE.md`, pas à corriger.

**L'invariant coûteux :** un fichier dont le contenu n'a pas changé n'est **jamais** revectorisé.
Pour un binaire, c'est le court-circuit d'empreinte de RAG-7 qui l'assure. Pour un Google Doc,
c'est `modifiedTime` — et il faut donc **ne même pas exporter** un Doc dont le `modifiedTime` n'a
pas bougé, sinon on paie l'export à chaque tour pour rien.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 (`@Scheduled`) · Spring AMQP · Flyway · PostgreSQL ·
JUnit 5 + AssertJ + Testcontainers. Aucune dépendance nouvelle.

**Ticket:** DRIVE-5 — Refléter les changements du Drive. **Prérequis stricts : RAG-7, DRIVE-3.**

## Global Constraints

Les mêmes que DRIVE-3, plus :

- **Une synchronisation en échec n'efface rien.** Elle se signale, et sera rejouée. Aucun geste
  destructeur ne doit se déclencher sur une lecture incomplète.
- **Un jeton de page perdu ou périmé retombe sur un balayage complet**, jamais sur « on repart de
  maintenant » — ce dernier laisserait la base dériver en silence, ce qui est précisément le
  mensonge que ce ticket veut supprimer.
- **La tâche planifiée ne fait pas le travail.** Elle publie un événement, le listener le consomme.
  Sinon deux exécutions se chevauchent au premier ralentissement.
- **Aucun ADR n'est écrit.** La synchronisation sur horloge et la règle du miroir en appelleront
  probablement un ; ils sont signalés dans la PR.

## Fichiers

**Créés**

| Fichier | Responsabilité |
|---|---|
| `…/application/command/RenameDocument.java` + handler | Le nom, et rien d'autre |
| `…/application/drive/DriveSynchroniser.java` | La boucle des changements, hors des bus |
| `…/application/drive/DriveChangeDecision.java` | Le geste que chaque changement appelle — **logique pure, donc testable sans Spring** |
| `…/domain/valueobject/DriveChange.java` | Un changement tel que Drive le rend |
| `…/domain/port/GoogleDriveChanges.java` | `startPageToken`, `changesSince(token)` |
| `…/domain/event/DriveSynchronisationRequested.java` | |
| `…/application/command/RequestDriveSynchronisation.java` + handler | |
| `…/infrastructure/drive/GoogleDriveChangesAdapter.java` | `changes.getStartPageToken`, `changes.list` |
| `…/infrastructure/scheduling/DriveSynchronisationScheduler.java` | `@Scheduled`, `@Profile("worker")` |
| `V18__add_knowledge_drive_connections_changes_token.sql` | Le jeton conservé |

**Modifiés**

| Fichier | Modification |
|---|---|
| `…/domain/entity/DriveConnection.java` | `changesPageToken`, et son remplacement |
| `…/domain/entity/Document.java` | `rename(String)` |
| `…/infrastructure/messaging/KnowledgeEventListener.java` | Un `@RabbitHandler` de plus |
| `…/infrastructure/messaging/KnowledgeMessagingConfiguration.java` | L'événement déclaré |
| `src/main/resources/application.yml` | L'intervalle |
| `compose.yaml`, `.env.example` | La variable |
| `CLAUDE.md` | Le récit |

---

### Task 1: Renommer un document sans le revectoriser

**Files:** `Document.rename`, `RenameDocument` + handler, `DocumentRenameTest`

- [ ] **Step 1: Écrire les tests qui échouent**

| Test | Ce qu'il vérifie |
|---|---|
| `changes_the_name_and_nothing_else` | Empreinte, format, taille, **statut** et motif inchangés |
| `keeps_the_chunks_of_the_document` | Aucun extrait effacé — c'est tout l'intérêt |
| `bounds_a_name_longer_than_the_column` | Comme `upload` et `replaceContent` |
| `refuses_a_blank_name` | |
| `refuses_to_rename_the_document_of_another_account` | Cloisonnement, par `findByIdAndOwnerId` |

**Attention :** `rename` doit réutiliser le `boundedFilename` privé déjà extrait par RAG-7, pas le
recopier. Et il ne doit **pas** toucher au statut : renommer un document `FAILED` ne le répare pas.

- [ ] **Step 2: Le code, puis vérifier, formater, committer**

Aucune route HTTP : cette commande n'a qu'un appelant, le synchroniseur. C'est un écart apparent à
l'habitude du dépôt — chaque commande y a jusqu'ici une route — et il est volontaire : rien dans le
produit ne demande de renommer un document à la main.

Message : `feat: un document se renomme sans que ses extraits soient recalculés`

---

### Task 2: Lire les changements du Drive

**Files:** `DriveChange`, `GoogleDriveChanges`, `GoogleDriveChangesAdapter`, `V18`,
`DriveConnection.changesPageToken`, le bouchon étendu

- [ ] **Step 1: Écrire les tests qui échouent**

| Test | Ce qu'il vérifie |
|---|---|
| `asks_google_for_a_starting_point_when_no_token_is_kept` | `changes.getStartPageToken` |
| `reads_the_changes_since_the_kept_token` | |
| `pages_through_more_changes_than_one_response_holds` | `nextPageToken` — même piège qu'en DRIVE-2 |
| `keeps_the_new_starting_token_at_the_end_of_a_page_run` | `newStartPageToken`, à conserver pour le tour suivant |
| `falls_back_on_a_full_scan_when_google_refuses_the_token` | Le `410 Gone` |

Le dernier est le plus important : un jeton périmé qui ferait simplement repartir « de maintenant »
laisserait la base dériver **en silence**, ce qui est exactement le mensonge que ce ticket
supprime.

- [ ] **Step 2: L'adapter**

`changes.list?pageToken=…&fields=changes(fileId,removed,file(id,name,mimeType,size,parents,trashed,modifiedTime,webViewLink)),nextPageToken,newStartPageToken`.
Borner le nombre de pages comme en DRIVE-2.

**`removed` et `file.trashed` sont deux choses différentes** : le premier dit que le fichier est
sorti du périmètre visible (supprimé, ou perdu par un partage), le second qu'il est à la corbeille.
Les deux mènent au retrait, mais un `removed` ne porte **aucun** `file`, donc aucun `parents` — le
code qui décide doit s'en accommoder plutôt que de déréférencer.

- [ ] **Step 3: Vérifier, formater, committer**

Message : `feat: lire les changements d'un Drive depuis un jeton conservé`

---

### Task 3: Décider du geste, en logique pure

**Files:** `DriveChangeDecision`, `DriveChangeDecisionTest`

**C'est la tâche qui porte la valeur du ticket.** Isoler la décision de son exécution la rend
testable sans Spring, sans Drive et sans base — et cette table de vérité est exactement le genre de
chose qui casse en silence.

- [ ] **Step 1: Écrire les tests qui échouent**

Un test par ligne du tableau de l'en-tête, plus les cas tordus :

| Test | Ce qu'il vérifie |
|---|---|
| `re_ingests_a_file_whose_content_changed` | |
| `imports_a_file_that_appeared_in_a_watched_folder` | |
| `removes_a_file_that_left_the_drive` | `removed`, donc sans `file` |
| `removes_a_file_sent_to_the_trash` | |
| `removes_a_file_moved_out_of_every_watched_folder` | |
| `renames_a_file_whose_name_changed_and_nothing_else` | |
| `does_nothing_for_a_file_moved_inside_the_same_watched_folder` | |
| `does_nothing_when_nothing_moved` | |
| `does_nothing_for_a_change_on_a_file_the_base_never_held` | Un changement hors périmètre |
| `does_not_re_export_a_google_document_whose_modified_time_has_not_changed` | **L'invariant coûteux** |
| `renames_and_re_ingests_a_file_whose_name_and_content_both_changed` | Les deux à la fois |

Le dernier mérite une décision explicite : `ReplaceDocumentContent` porte déjà le nom, donc un
contenu **et** un nom modifiés se règlent en une seule commande. Ne pas dispatcher les deux.

- [ ] **Step 2: Le code, puis vérifier, formater, committer**

Message : `feat: décider du geste qu'appelle chaque changement du Drive`

---

### Task 4: La boucle, l'horloge et l'événement

**Files:** `DriveSynchroniser`, `DriveSynchronisationRequested`, `RequestDriveSynchronisation` +
handler, `DriveSynchronisationScheduler`, le listener, la configuration, `DriveSynchronisationTest`

- [ ] **Step 1: Écrire les tests qui échouent**

`DriveSynchronisationTest`, en `@ActiveProfiles("worker")` sur le modèle de
`KnowledgeEventListenerTest` — les sept scénarios du ticket, de bout en bout.

**Ne pas laisser l'horloge déclencher les tests :** poser un intervalle très long dans la
configuration de test et publier l'événement à la main, comme le fait déjà
`KnowledgeEventListenerTest`. Un test qui attend un `@Scheduled` est un test qui échoue le jour où
la machine rame.

- [ ] **Step 2: La boucle**

`DriveSynchroniser` n'est **pas** un `CommandHandler` : il est appelé par le listener, hors
transaction, et dispatche une commande par geste. Une exception en cours de lecture **arrête la
boucle sans rien effacer** et fait écrire le bilan en échec par une seconde commande (ADR-0028).

**Le jeton de page ne se conserve qu'après un tour complet réussi.** Le conserver au fil de l'eau
ferait perdre les changements d'une page dont le traitement a échoué.

- [ ] **Step 3: L'horloge**

`@Scheduled(fixedDelayString = "${secondbrain.drive.synchronisation-interval}")`, `@Profile("worker")`,
**`fixedDelay` et non `fixedRate`** : le second lancerait un tour toutes les N minutes même si le
précédent n'est pas fini.

Elle **publie**, elle ne travaille pas. Deux raisons : le travail appartient au listener, qui est à
`concurrency: 1` et sérialise donc les tours ; et une tâche planifiée qui appelle directement
tiendrait son thread pendant tout l'import.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: le Drive se synchronise sur horloge et la base reflète ses changements`

---

### Task 5: La suite complète, puis la documentation

- [ ] `docker compose down` puis `gtest build`, **une seule fois**.
- [ ] `CLAUDE.md` : une sous-section « Le flux de la synchronisation d'un Drive » qui dit la règle
  du miroir, la table des gestes, l'invariant « rien de rechangé n'est revectorisé » et ses deux
  mécanismes (empreinte pour un binaire, `modifiedTime` pour un Doc), le jeton de page conservé
  **après** un tour complet et son repli sur balayage complet, `fixedDelay` plutôt que `fixedRate`,
  la tâche qui publie plutôt qu'elle ne travaille, et — le point à ne pas oublier — **le renommage
  qui laisse les vecteurs calculés sous l'ancien nom**, avec ce que ça nuance de la promesse de
  RAG-5.
- [ ] Message : `docs: documente la synchronisation d'un Drive surveillé`
