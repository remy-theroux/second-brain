# Réunir sources et documents sur un seul écran — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Depuis un seul écran, déposer un fichier à la main, connecter un dossier Drive, et voir
d'où vient chaque document. Les fonctionnalités de DRIVE-1 à DRIVE-5 n'ont aujourd'hui **aucune
interface**.

**Architecture:** Front seul, sur les routes déjà livrées. `DocumentsView` devient « Sources et
documents » : deux sources cohabitent en haut — le dépôt manuel et les dossiers Drive — et la liste
unifiée dessous. Deux écrans séparés diraient faussement qu'il s'agit de deux bases.

**Ce qui sort d'un `.vue` pour être testé**, parce que c'est ce qui casse en silence :

- **`src/api/client.js`** — les nouvelles routes et la traduction de leurs refus. C'est là que
  vivent les tests, comme le veut ADR-0016.
- **`driveMessages.js`** — la table des codes de retour d'autorisation. Le serveur redirige avec un
  **code**, pas un message (ADR-0017) ; les libellés français vivent côté front, comme
  `VERIFICATION_MESSAGES`. La table sort du `.vue` pour la même raison que `documentStatus.js` :
  un code inconnu qui n'afficherait rien ne se verrait pas.

**Tech Stack:** Vue 3 · PrimeVue 4 (Aura) · Vitest (jsdom). Aucune dépendance nouvelle, aucune
ligne de Java.

**Ticket:** DRIVE-7 — Réunir sources et documents sur un seul écran. Prérequis : DRIVE-1 à DRIVE-5.

## Global Constraints

- **Aucune couleur en dur, aucun `rem` nu.** Tokens `--p-*` d'Aura et `--sb-*` du projet. Un token
  nouveau se définit dans `src/assets/main.css` et **nulle part ailleurs**.
- **Tout composant partagé nouveau apparaît dans `/design-system` au même commit.** Un composant
  absent de cette page n'est pas partagé.
- **Aucun `fetch` hors de `src/api/`.** Un `401` déconnecte, comme partout.
- **Aucun test de rendu** (ADR-0016).
- **Les messages d'erreur viennent du serveur et s'affichent tels quels.** La seule exception est
  le code de retour d'autorisation, qui voyage en query string — c'est ADR-0017 et ADR-0022.
- **Langue :** code, commentaires et libellés `describe`/`it` en anglais ; libellés d'écran en
  français.
- `make format-front` avant tout commit.

## Fichiers

**Créés**

| Fichier | Responsabilité |
|---|---|
| `frontend/src/components/driveMessages.js` + `.spec.js` | Les codes de retour et leurs libellés |
| `frontend/src/components/DriveSourceCard.vue` | Une source Drive : nom, état, dernière synchro |
| `frontend/src/components/DriveFolderPicker.vue` | Le parcours et le choix d'un dossier |
| `frontend/src/components/DocumentSourceTag.vue` | La provenance d'un document, dans la liste |

**Modifiés**

| Fichier | Modification |
|---|---|
| `frontend/src/api/client.js` + `.spec.js` | Les routes Drive et leurs refus |
| `frontend/src/views/DocumentsView.vue` | Les sources au-dessus de la liste |
| `frontend/src/views/DesignSystemView.vue` | Les trois composants partagés |
| `frontend/src/assets/main.css` | Un token si nécessaire |
| `CLAUDE.md` | Le récit de l'écran |

---

### Task 1: Les routes Drive dans `src/api/`

**Files:** `client.js`, `client.spec.js`

- [ ] **Step 1: Écrire les tests qui échouent**

`fetch` est bouché par `vi.stubGlobal`, jamais une vraie requête — c'est la convention du fichier.
Une fonction par route, et **un test par refus** : c'est la traduction des refus qui casse en
silence, pas l'appel nominal.

| Fonction | Route | Refus à traduire |
|---|---|---|
| `startDriveAuthorization(token)` | `POST /api/drive/authorizations` | `401` |
| `fetchDriveConnection(token)` | `GET /api/drive/connection` | `404` → `null`, **pas une erreur** : « pas de Drive connecté » est un état normal, pas un échec |
| `disconnectDrive(token)` | `DELETE /api/drive/connection` | `404` |
| `browseDriveFolders(token, parentId)` | `GET /api/drive/folders` | `409` → « connectez un compte », `503` → Google injoignable |
| `listWatchedFolders(token)` | `GET /api/drive/watched-folders` | |
| `watchDriveFolder(token, folderId)` | `POST /api/drive/watched-folders` | `409` → déjà couvert, `404` → dossier inconnu |
| `unwatchDriveFolder(token, id)` | `DELETE /api/drive/watched-folders/{id}` | `404` |
| `importWatchedFolder(token, id)` | `POST /api/drive/watched-folders/{id}/import` | `404` |

**Le `404` de `fetchDriveConnection` rend `null`, et c'est la seule subtilité de cette tâche** :
le traiter comme une erreur ferait afficher un message d'échec à tout utilisateur qui n'a jamais
connecté de Drive, c'est-à-dire à l'ouverture du premier écran.

**Le corps n'est pas garanti JSON** (proxy à terre, HTML 502) : un `.catch(() => null)` sur le
`json()`, comme le fait déjà `uploadDocument`. Un parse qui échoue ne doit pas remplacer le message
métier par une erreur de syntaxe.

- [ ] **Step 2: Le code, puis vérifier, formater, committer**

Message : `feat: le front sait parler aux routes Drive`

---

### Task 2: Les codes de retour d'autorisation

**Files:** `driveMessages.js` + `.spec.js`, `DocumentsView.vue`

- [ ] **Step 1: Écrire les tests qui échouent**

```js
describe('driveMessage', () => {
  it('has a label for every code the server can redirect with', ...)   // ok, refus, lien-invalide, echec
  it('says nothing when no code is present', ...)                       // undefined, null, ''
  it('falls back on a generic failure for an unknown code', ...)         // et NON sur du silence
})
```

Le dernier point compte : un code que le front ne connaît pas doit produire **un message**, pas
rien. Le silence ferait croire que la connexion a marché.

Chaque entrée porte aussi sa **sévérité** (`success` pour `ok`, `error` pour les trois autres) :
la vue ne doit pas la déduire du code.

- [ ] **Step 2: Lire le code, puis l'effacer de l'URL**

`DocumentsView` lit `route.query.drive` au montage, affiche le message, puis **retire le paramètre**
par un `router.replace`. Sans ça, un F5 rejoue le message d'une connexion faite dix minutes plus
tôt, et il reste dans l'historique du navigateur — exactement le reproche fait au jeton de
vérification (ADR-0007).

- [ ] **Step 3: Vérifier, formater, committer**

Message : `feat: le retour d'autorisation Google se lit et s'efface de l'URL`

---

### Task 3: Les sources sur l'écran

**Files:** `DriveSourceCard.vue`, `DriveFolderPicker.vue`, `DocumentsView.vue`,
`DesignSystemView.vue`

- [ ] **Step 1: L'état sans Drive**

Aucun compte connecté → une invitation à en connecter un, **et le dépôt manuel reste disponible**.
C'est le premier scénario du ticket, et c'est aussi l'état de tout nouvel utilisateur : il ne doit
ressembler ni à une erreur, ni à un écran vide.

- [ ] **Step 2: Le sélecteur de dossier**

`DriveFolderPicker` parcourt (`browseDriveFolders`), descend, et remonte — un fil d'Ariane, pas un
arbre : le parcours se fait par appels successifs, un niveau à la fois, et reconstruire un arbre
complet demanderait de balayer tout le Drive.

**Il n'affiche que des dossiers** : c'est déjà ce que la route garantit, mais le composant ne doit
pas laisser croire qu'on y choisit un fichier.

- [ ] **Step 3: L'état d'un dossier surveillé**

`DriveSourceCard` montre le nom, **le nombre de documents apportés**, la dernière synchronisation,
et l'échec avec son motif le cas échéant. Les deux derniers viennent du bilan que DRIVE-3 persiste.

- [ ] **Step 4: Le design system**

Les trois composants partagés y apparaissent, **dans chacun de leurs états** — y compris l'état
d'échec et l'état « jamais synchronisé ». C'est le seul contrôle de rendu du projet.

- [ ] **Step 5: Vérifier, formater, committer**

Message : `feat: l'écran des documents porte aussi les sources Drive`

---

### Task 4: La provenance dans la liste

**Files:** `DocumentSourceTag.vue`, `DocumentsView.vue`, `DesignSystemView.vue`

- [ ] **Step 1: La colonne de provenance**

Chaque document indique d'où il vient. Un document importé porte un lien **« ouvrir dans Drive »**
vers son `driveWebViewLink`, en `target="_blank" rel="noopener"` — c'est une URL tierce, et
`noopener` n'est pas décoratif.

- [ ] **Step 2: L'avertissement de suppression**

Supprimer un document **importé** doit avertir qu'il **reviendra à la prochaine synchronisation**.
C'est le `ConfirmPopup` existant, dont le message dépend de la provenance. Sans cet avertissement,
l'utilisateur croit avoir retiré quelque chose de sa base et le voit réapparaître sans comprendre.

- [ ] **Step 3: Vérifier, formater, committer**

Message : `feat: chaque document dit d'où il vient`

---

### Task 5: Le build, puis la documentation

- [ ] `gfront npm run test:unit` et `gfront npm run build`.
- [ ] **Vérifier `/design-system` à l'œil** sur la pile lancée : c'est ce que ADR-0016 met à la
  place des tests de rendu, donc le sauter revient à ne rien vérifier du tout.
- [ ] `CLAUDE.md` : réécrire le paragraphe de `DocumentsView` — l'écran porte maintenant deux
  sources et une liste unifiée ; dire le code de retour lu **puis effacé de l'URL** et pourquoi ;
  le `404` de la connexion traduit en `null` et pourquoi ; l'avertissement de suppression d'un
  document importé ; et `driveMessages.js` sorti du `.vue` pour la même raison que
  `documentStatus.js`.
- [ ] Message : `docs: documente l'écran des sources et des documents`
