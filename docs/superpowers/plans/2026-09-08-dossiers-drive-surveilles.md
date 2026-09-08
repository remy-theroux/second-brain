# Choisir les dossiers Drive à surveiller — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Désigner explicitement la partie du Drive qui alimente la base de connaissance :
parcourir les dossiers, en mettre sous surveillance, en retirer. **Aucun fichier n'est lu.**

**Architecture:** Trois routes de lecture-écriture sur une entité neuve, et un premier vrai
dialogue avec l'API Drive.

- `GET /api/drive/folders` et `GET /api/drive/folders?parent=<id>` parcourent **les dossiers
  seulement** (`mimeType = 'application/vnd.google-apps.folder'`, filtré par parent). La liste
  des fichiers n'a rien à faire dans un sélecteur de dossier.
- `POST /api/drive/watched-folders` met un dossier sous surveillance, **par son identifiant
  Drive**, jamais par son chemin : le renommer ou le déplacer ne doit pas casser la surveillance,
  exactement comme l'identité d'un document est son contenu et non son nom.
- `GET /api/drive/watched-folders` les liste, `DELETE /api/drive/watched-folders/{id}` en retire
  un — **sans supprimer le moindre document déjà importé**, le miroir étant la responsabilité de
  DRIVE-5.

Ce ticket apporte la pièce que DRIVE-1 avait laissée en suspens : **le jeton d'accès**. Un jeton
de rafraîchissement ne vaut rien tant que personne ne l'échange. `GoogleAccessTokens` le fait, et
garde le jeton obtenu en mémoire jusqu'à son expiration — un aller-retour vers Google avant chaque
`files.list` doublerait la latence du parcours pour rien. **C'est aussi ici que se referme le
scénario de DRIVE-1 resté sans déclencheur** : un rafraîchissement que Google refuse par
`invalid_grant` signifie que l'utilisateur a retiré l'accès depuis son compte Google, et la
connexion passe alors en `NEEDS_RECONNECTION`.

**Le refus du dossier déjà couvert** demande de remonter les ancêtres du candidat par
`files.get?fields=parents`, jusqu'à la racine ou jusqu'à un dossier surveillé. C'est N appels pour
N niveaux de profondeur, et c'est acceptable : ça n'arrive qu'à la mise sous surveillance, un
geste rare.

**Tech Stack:** Java 25 · Spring Boot 4.0.7 · Flyway · PostgreSQL · JUnit 5 + AssertJ +
Testcontainers. Aucune dépendance nouvelle.

**Ticket:** DRIVE-2 — Choisir les dossiers Drive à surveiller. Prérequis : DRIVE-1.

## Global Constraints

Les mêmes que DRIVE-1, plus :

- **Aucune route de ce ticket ne lit un contenu de fichier.** Si le code appelle
  `files.get?alt=media` ou `files.export`, il est hors périmètre.
- **Le parcours est cloisonné par le compte** : c'est la connexion Drive du propriétaire du jeton
  qui est utilisée, jamais un identifiant de connexion venu de la requête.
- **Aucun ADR n'est écrit.** Voir les deux points signalés dans la PR.

## Fichiers

**Créés — domaine**

| Fichier | Responsabilité |
|---|---|
| `…/domain/entity/WatchedFolder.java` | Un dossier surveillé, rattaché à la connexion |
| `…/domain/valueobject/DriveFolder.java` | Un dossier tel que Drive le rend : identifiant, nom |
| `…/domain/valueobject/DriveAccessToken.java` | Le jeton d'accès et son expiration, `toString()` masqué |
| `…/domain/port/WatchedFolderRepository.java` | |
| `…/domain/port/GoogleDriveFolders.java` | `children`, `folder`, `ancestors` |
| `…/domain/port/GoogleAccessTokens.java` | Échange un `RefreshToken` contre un `DriveAccessToken` |
| `…/domain/exception/DriveNotConnectedException.java` | « Connectez un compte Google… » |
| `…/domain/exception/FolderAlreadyCoveredException.java` | « Ce dossier est déjà couvert par… » |
| `…/domain/exception/DriveAuthorizationRevokedException.java` | `invalid_grant` |
| `…/domain/exception/WatchedFolderNotFoundException.java` | |

**Créés — application**

| Fichier | Responsabilité |
|---|---|
| `…/application/query/BrowseDriveFolders.java` + handler + `DriveFolderView.java` | Le parcours |
| `…/application/query/ListWatchedFolders.java` + handler + `WatchedFolderView.java` | La liste |
| `…/application/command/WatchDriveFolder.java` + handler | La mise sous surveillance et son refus |
| `…/application/command/UnwatchDriveFolder.java` + handler | Le retrait |

**Créés — infrastructure**

| Fichier | Responsabilité |
|---|---|
| `…/infrastructure/drive/GoogleDriveFoldersAdapter.java` | `files.list` et `files.get`, seuls endroits qui parlent à Drive |
| `…/infrastructure/drive/CachingGoogleAccessTokens.java` | L'échange, et son cache mémoire |
| `…/infrastructure/drive/GoogleFileListResponse.java`, `GoogleFileResponse.java` | Les corps JSON |
| `…/infrastructure/persistence/JpaWatchedFolderRepositoryAdapter.java` + `SpringDataWatchedFolderRepository.java` | |
| `…/infrastructure/web/BrowseDriveFoldersController.java` | `GET /api/drive/folders` |
| `…/infrastructure/web/WatchDriveFolderController.java` | `POST /api/drive/watched-folders` |
| `…/infrastructure/web/ListWatchedFoldersController.java` | `GET /api/drive/watched-folders` |
| `…/infrastructure/web/UnwatchDriveFolderController.java` | `DELETE /api/drive/watched-folders/{id}` |
| `V15__create_knowledge_drive_watched_folders.sql` | La table |

**Créés — tests**

| Fichier | Responsabilité |
|---|---|
| `src/test/…/knowledge/FakeGoogleDriveConfiguration.java` | Un Drive bouchonné : une arborescence de dossiers programmable |
| `src/test/…/knowledge/infrastructure/web/DriveFolderBrowsingTest.java` | Les deux scénarios de parcours + le refus sans connexion |
| `src/test/…/knowledge/infrastructure/web/WatchedFolderTest.java` | Les quatre scénarios de surveillance |

---

### Task 1: Le jeton d'accès, et la connexion qui se signale à renouveler

**Files:**
- Create: `DriveAccessToken`, `GoogleAccessTokens`, `DriveAuthorizationRevokedException`,
  `CachingGoogleAccessTokens`
- Modify: le bouchon Google de DRIVE-1, pour qu'il sache aussi rendre un jeton d'accès

**Interfaces:**
- Consomme : `RefreshToken`, `DriveConnection`.
- Produit : `DriveAccessToken GoogleAccessTokens.forConnection(DriveConnection)`.

- [ ] **Step 1: Écrire les tests qui échouent**

`CachingGoogleAccessTokensTest`, **unitaire pur** — l'échange est bouché par un
`GoogleAccessTokens` de test, ce qui se teste ici c'est le cache :

| Test | Ce qu'il vérifie |
|---|---|
| `exchanges_once_and_reuses_the_token_until_it_expires` | Deux appels rapprochés = **un** échange |
| `exchanges_again_once_the_token_has_expired` | Avec une `Clock` avancée, un second échange |
| `exchanges_again_shortly_before_expiry` | Une marge — un jeton qui expire dans 10 s ne part pas en voyage |
| `keeps_one_token_per_connection` | Deux connexions ne se volent pas leur jeton |

Le cache prend l'horloge par constructeur (`Clock`, bean existant) : sans ça, ces tests
attendraient une heure.

- [ ] **Step 2: Écrire le code**

`CachingGoogleAccessTokens` fait le `POST oauth2.googleapis.com/token` avec
`grant_type=refresh_token`, et garde le résultat dans une `ConcurrentHashMap` clé sur
l'identifiant de la connexion. Une réponse `400 invalid_grant` lève
`DriveAuthorizationRevokedException` — **et rien d'autre ne la lève** : c'est le seul signal que
Google donne d'un accès retiré.

- [ ] **Step 3: Faire passer la connexion en `NEEDS_RECONNECTION`**

C'est le point qui referme le scénario laissé ouvert par DRIVE-1. `DriveAuthorizationRevokedException`
hérite de `RuntimeException`, donc **elle annule la transaction du bus** : écrire le statut dans
cette transaction-là le perdrait. C'est exactement la situation d'ADR-0028, et la réponse est la
même — une **seconde** commande, `MarkDriveConnectionExpired`, dispatchée par le contrôleur qui a
rattrapé l'exception, avant de rendre son refus.

Le vérifier par un test : après un parcours qui a échoué sur un accès révoqué,
`GET /api/drive/connection` doit rendre `NEEDS_RECONNECTION`. **Attention à la règle des tests** :
l'appel refusé marque la transaction englobante rollback-only, donc ce contrôle-là se fait par le
port, pas par une seconde requête HTTP.

- [ ] **Step 4: Vérifier, formater, committer**

Message : `feat: le jeton d'accès Drive s'obtient, se garde et signale un accès retiré`

---

### Task 2: Le parcours des dossiers

**Files:**
- Create: `DriveFolder`, `GoogleDriveFolders`, `GoogleDriveFoldersAdapter`, les deux records JSON,
  `BrowseDriveFolders` + handler + `DriveFolderView`, `BrowseDriveFoldersController`,
  `DriveNotConnectedException`
- Create: `FakeGoogleDriveConfiguration`, `DriveFolderBrowsingTest`

- [ ] **Step 1: Le bouchon d'abord**

`FakeGoogleDriveConfiguration` porte une arborescence de dossiers programmable :
`fakeDrive.put(parentId, List.of(folder("a1", "Notes"), …))`, plus un `clear()` appelé en
`@BeforeEach` — le bean est partagé par tout le contexte et le rollback ne le remet pas à zéro.
Il sait aussi lever `GoogleDriveUnavailableException` et `DriveAuthorizationRevokedException` sur
commande.

- [ ] **Step 2: Écrire les tests qui échouent**

| Méthode de test | Scénario du ticket |
|---|---|
| `lists_the_top_level_folders_without_the_files` | Parcourir la racine — et le bouchon contient un fichier, qui **ne doit pas** sortir |
| `lists_the_subfolders_of_a_folder` | Descendre dans un dossier |
| `refuses_to_browse_without_a_connected_account` | Parcourir sans connexion — dernier appel du test |
| `refuses_an_anonymous_request` | `401` |

- [ ] **Step 3: L'adapter**

`children(accessToken, parentId)` fait
`GET /drive/v3/files?q='<parent>' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id,name),nextPageToken`.
La racine est le mot-clé `root`, pas une valeur vide.

**Paginer.** `files.list` rend 100 entrées par défaut et un `nextPageToken` ; un Drive personnel a
des dossiers avec plus de cent sous-dossiers, et ne pas suivre le jeton les perd en silence — le
pire mode d'échec possible pour un sélecteur.

- [ ] **Step 4: La query et la route**

`GET /api/drive/folders?parent=` rend une liste de `{ id, name }`. Sans connexion : `409` avec
`ErrorResponse` — « Connectez un compte Google pour parcourir votre Drive. » Un `404` mentirait
sur la cause, un `403` parlerait de droits qui ne sont pas en jeu.

L'appel à Google a lieu **dans la transaction `readOnly` du query bus**, comme la vectorisation de
la recherche : c'est tenable pour une lecture qui n'écrit rien.

- [ ] **Step 5: Vérifier, formater, committer**

Message : `feat: parcourir les dossiers d'un Drive connecté`

---

### Task 3: Mettre un dossier sous surveillance, et l'en retirer

**Files:**
- Create: `WatchedFolder`, `WatchedFolderRepository` + adapters, `V14__…sql`,
  `WatchDriveFolder` + handler, `UnwatchDriveFolder` + handler, `ListWatchedFolders` + handler +
  `WatchedFolderView`, les trois contrôleurs, `FolderAlreadyCoveredException`,
  `WatchedFolderNotFoundException`
- Create: `WatchedFolderTest`

- [ ] **Step 1: Écrire les tests qui échouent**

| Méthode de test | Scénario du ticket |
|---|---|
| `watches_a_folder_of_my_drive` | Il apparaît dans mes sources, **avec son nom** |
| `watches_a_second_folder_alongside_the_first` | Les deux apparaissent |
| `refuses_a_folder_already_covered_by_a_watched_ancestor` | Refus, en indiquant qu'il est déjà couvert |
| `unwatches_a_folder_without_touching_the_connection` | Il quitte les sources, la connexion reste active |
| `unwatches_a_folder_without_removing_the_documents_it_brought` | La liste des documents ne bouge pas |
| `refuses_to_watch_without_a_connected_account` | Dernier appel du test |
| `refuses_an_anonymous_request` | `401` |

- [ ] **Step 2: La table**

```sql
CREATE TABLE knowledge_drive_watched_folders (
    id uuid PRIMARY KEY,
    connection_id uuid NOT NULL REFERENCES knowledge_drive_connections (id) ON DELETE CASCADE,
    drive_folder_id varchar(255) NOT NULL,
    name varchar(255) NOT NULL,
    watched_at timestamptz NOT NULL,
    UNIQUE (connection_id, drive_folder_id)
);
```

`ON DELETE CASCADE` sur la connexion : révoquer un compte Google emporte ses dossiers surveillés,
et **pas** les documents qu'ils ont apportés — c'est ce que dit DRIVE-1. L'`UNIQUE` est le filet
sous le contrôle applicatif, comme `(owner_id, checksum)` l'est pour un document.

- [ ] **Step 3: Le contrôle de couverture**

Le handler remonte les ancêtres du dossier candidat par `ancestors(accessToken, folderId)`, qui
enchaîne des `files.get?fields=parents` jusqu'à la racine. Si l'un d'eux est déjà surveillé, il
lève `FolderAlreadyCoveredException`, dont le message **nomme le dossier couvrant** — « Ce dossier
est déjà couvert par « Notes ». » : dire seulement « déjà couvert » obligerait à chercher lequel.

**Poser une borne de profondeur** (par exemple 50) et lever plutôt que boucler : un cycle dans les
parents ne devrait pas exister, mais Drive autorise plusieurs parents par fichier depuis
longtemps, et une boucle infinie dans une transaction est le genre de panne qui immobilise le
serveur sans rien dire.

**Ce que ce contrôle ne fait pas, et qui est assumé :** surveiller un dossier qui est l'**ancêtre**
d'un dossier déjà surveillé n'est pas refusé. Le ticket ne le demande pas, mais son motif — « pour
qu'aucun fichier ne soit balayé deux fois » — vaut symétriquement. **Le dire dans la PR** ; le
corriger demanderait de décider quoi faire du dossier devenu redondant, ce qui est une décision de
produit.

- [ ] **Step 4: Les trois routes**

- `POST /api/drive/watched-folders` avec `{ "folderId": "…" }` → `201` sans corps. `409` sur un
  dossier déjà couvert **et** sur un dossier déjà surveillé, `404` sur un dossier que Drive ne
  connaît pas, `409` sans connexion.
- `GET /api/drive/watched-folders` → la liste, `{ id, driveFolderId, name, watchedAt }`. Sans
  connexion, une liste **vide** et non un refus : c'est une lecture, et l'écran de DRIVE-7 doit
  pouvoir l'appeler avant d'avoir connecté quoi que ce soit.
- `DELETE /api/drive/watched-folders/{id}` → `204`, `404` si inconnu ou appartenant à autrui.

- [ ] **Step 5: Vérifier, formater, committer**

Message : `feat: mettre un dossier Drive sous surveillance et l'en retirer`

---

### Task 4: La suite complète, puis la documentation

- [ ] **Step 1:** `docker compose down` puis `gtest build`.
- [ ] **Step 2:** Dans `CLAUDE.md`, une sous-section « Le flux du choix des dossiers surveillés »
  qui dit : les quatre routes ; le dossier retenu **par son identifiant Drive et jamais par son
  chemin**, et pourquoi ; le refus du dossier déjà couvert, sa borne de profondeur et son angle
  mort assumé ; le cache de jeton d'accès et sa marge ; `invalid_grant` comme **seul** signal d'un
  accès retiré, et le fait que le statut s'écrit dans une seconde transaction (ADR-0028) ; la
  pagination de `files.list`, sans laquelle un dossier de plus de cent sous-dossiers en perd la
  moitié en silence. Compléter « Persistance » de la nouvelle table et de ses deux cascades.
- [ ] **Step 3:** Committer — `docs: documente le choix des dossiers Drive surveillés`
