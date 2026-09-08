# Écran de dépôt et de suivi des documents — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Achever RAG-11 — déposer **plusieurs** fichiers d'un coup, au glisser-déposer comme
au clic, et voir leur statut avancer **sans recharger la page**.

**Architecture:** L'écran existe déjà (`DocumentsView`) avec le dépôt d'un fichier, la liste et
la suppression confirmée. Il manque deux choses, et deux seulement :

1. **Le multi-fichiers.** `FileUpload` passe du mode `basic` au mode avancé, qui porte sa propre
   zone de glisser-déposer, avec `multiple`. Les fichiers sont envoyés **en séquence** — le back
   n'a qu'une route `POST /api/documents` par fichier — et chaque refus est rendu **nommément**,
   sous le nom du fichier qu'il vise : un message global mentirait dès qu'un dépôt sur trois est
   refusé.
2. **Le rafraîchissement.** Tant qu'un document de la liste n'est pas dans un statut terminal, la
   liste se relit toutes les 2 s. Dès qu'aucun ne l'est plus, l'horloge s'arrête — et elle
   s'arrête aussi en quittant l'écran.

Le savoir « quels statuts sont terminaux » ne peut pas vivre dans la vue : il est déjà à moitié
dans `DocumentStatusTag`, sous forme de deux tables de libellés. Il devient un module `.js`
partagé, `documentStatus.js`, sur le modèle d'`answerSegments.js` — c'est-à-dire **hors d'un
`.vue` pour être testable**, puisque c'est exactement le genre de logique qui casse en silence :
une horloge qui ne s'arrête jamais ne se voit pas à l'écran, elle se voit dans les logs du
serveur, six mois plus tard.

**Tech Stack:** Vue 3 · PrimeVue 4 (thème Aura) · Vitest (jsdom). Aucune dépendance nouvelle,
aucune ligne de Java, aucune migration.

**Ticket:** RAG-11 — Offrir l'écran de dépôt et de suivi des documents.

## Global Constraints

- **Branche :** `feat/rag-11-ecran-documents`, dans `/home/remy-theroux/projects/second-brain`.
- **Aucun Node sur l'hôte.** Définir cette fonction **une fois** au début de la session :

  ```bash
  gfront() {
    docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp \
      -v "$PWD/frontend":/app -w /app \
      node:24-alpine "$@"
  }
  ```

- **Langue :** le code, les commentaires et les libellés `describe`/`it` de Vitest sont en
  **anglais** ; les libellés d'écran et les messages de commit sont en **français**. Les messages
  de refus **viennent du serveur** et s'affichent tels quels — le front n'en réécrit aucun.
- **Style :** aucune couleur en dur, aucun `rem` nu. Tokens `--p-*` d'Aura et `--sb-*` du projet.
  Un token nouveau se définit dans `src/assets/main.css` et **nulle part ailleurs**.
- **Design system :** tout composant partagé ou token nouveau apparaît dans
  `/design-system` **au même commit**. Ce plan n'introduit ni l'un ni l'autre — `documentStatus.js`
  n'est pas un composant — donc `DesignSystemView` n'est pas touché.
- **Tests :** aucun test de rendu (ADR-0016). Ce qui se teste, c'est le module `.js` extrait.
- **Formatage :** `make format-front` avant tout commit.
- **Commits :** préfixe conventionnel en minuscule, description en français, un commit par tâche,
  tests verts.
- **Aucun ADR.** Rien ici ne ferme d'alternative crédible : le mode avancé de `FileUpload` et un
  `setTimeout` réarmé sont des choix de mise en œuvre, pas des décisions d'architecture.

## Fichiers

**Créés**

| Fichier | Responsabilité |
|---|---|
| `frontend/src/components/documentStatus.js` | Les libellés, les sévérités, et ce qui dit si un statut est **terminal** |
| `frontend/src/components/documentStatus.spec.js` | Ses tests |

**Modifiés**

| Fichier | Modification |
|---|---|
| `frontend/src/components/DocumentStatusTag.vue` | N'héberge plus les tables : il les importe |
| `frontend/src/views/DocumentsView.vue` | Dépôt multiple, refus nommés, horloge de rafraîchissement |
| `frontend/src/assets/main.css` | Le token de l'intervalle n'existe pas ; rien à ajouter sauf si une classe partagée naît |
| `CLAUDE.md` | Le récit de l'écran : multi-fichiers et rafraîchissement |

---

### Task 1: Extraire ce que la vue doit savoir d'un statut

**Files:**
- Create: `frontend/src/components/documentStatus.js`
- Create: `frontend/src/components/documentStatus.spec.js`
- Modify: `frontend/src/components/DocumentStatusTag.vue`

**Interfaces:**
- Consomme : rien.
- Produit : `STATUS_LABELS`, `STATUS_SEVERITIES`, `isSettled(status)`, `hasUnsettled(documents)`
  — consommés par la tâche 2 et par `DocumentStatusTag`.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `frontend/src/components/documentStatus.spec.js` :

```js
import { describe, expect, it } from 'vitest'
import { hasUnsettled, isSettled, STATUS_LABELS, STATUS_SEVERITIES } from './documentStatus'

describe('isSettled', () => {
  it('holds READY and FAILED as settled', () => {
    expect(isSettled('READY')).toBe(true)
    expect(isSettled('FAILED')).toBe(true)
  })

  it('holds PENDING and EXTRACTED as still moving', () => {
    expect(isSettled('PENDING')).toBe(false)
    expect(isSettled('EXTRACTED')).toBe(false)
  })

  // A status the server adds without the front knowing must keep the clock running rather
  // than freeze a list that is still moving.
  it('holds an unknown status as still moving', () => {
    expect(isSettled('WHATEVER')).toBe(false)
  })
})

describe('hasUnsettled', () => {
  it('is false on an empty list', () => {
    expect(hasUnsettled([])).toBe(false)
  })

  it('is false when every document has settled', () => {
    expect(hasUnsettled([{ status: 'READY' }, { status: 'FAILED' }])).toBe(false)
  })

  it('is true as soon as one document is still moving', () => {
    expect(hasUnsettled([{ status: 'READY' }, { status: 'PENDING' }])).toBe(true)
  })
})

describe('the status tables', () => {
  it('label and colour every status', () => {
    for (const status of ['PENDING', 'EXTRACTED', 'READY', 'FAILED']) {
      expect(STATUS_LABELS[status]).toBeTruthy()
      expect(STATUS_SEVERITIES[status]).toBeTruthy()
    }
  })
})
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
gfront npx vitest run src/components/documentStatus.spec.js
```

Attendu : **échec de résolution**, `Failed to resolve import "./documentStatus"`.

- [ ] **Step 3: Écrire le module**

Créer `frontend/src/components/documentStatus.js`. Y **déplacer** les deux tables actuellement
dans `DocumentStatusTag.vue`, commentaires compris, et ajouter les deux prédicats :

```js
// The status travels as a code, like everything the API serialises from an enum; the label is
// a screen matter, and this copy is assumed — ADR-0022. The failure reason, on the other hand,
// comes from the server and is displayed as is — the front rewrites none.
export const STATUS_LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  READY: 'Prêt à être interrogé',
  FAILED: 'Traitement en échec',
}

// The severity is a rendering decision, not data: "pending" is neither a success nor an error.
export const STATUS_SEVERITIES = {
  PENDING: 'secondary',
  EXTRACTED: 'info',
  READY: 'success',
  FAILED: 'danger',
}

const SETTLED = ['READY', 'FAILED']

export function isSettled(status) {
  return SETTLED.includes(status)
}

export function hasUnsettled(documents) {
  return documents.some((document) => !isSettled(document.status))
}
```

- [ ] **Step 4: Faire importer `DocumentStatusTag`**

Remplacer le `<script setup>` de `frontend/src/components/DocumentStatusTag.vue` par :

```vue
<script setup>
import Tag from 'primevue/tag'
import { STATUS_LABELS, STATUS_SEVERITIES } from './documentStatus'

defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <Tag :value="STATUS_LABELS[status] ?? status" :severity="STATUS_SEVERITIES[status] ?? 'secondary'" />
</template>
```

Le `<template>` ne change que par le nom des deux tables.

- [ ] **Step 5: Vérifier**

```bash
gfront npx vitest run src/components/documentStatus.spec.js
gfront npm run test:unit
gfront npm run build
```

Attendu : tous verts, et le build passe — c'est lui qui compile les templates.

- [ ] **Step 6: Formater et committer**

```bash
make format-front
git add frontend/src/components
git commit
```

Message : `refactor: les statuts d'un document quittent le composant pour un module testable`

---

### Task 2: Déposer plusieurs fichiers d'un coup

**Files:**
- Modify: `frontend/src/views/DocumentsView.vue`

**Interfaces:**
- Consomme : `uploadDocument`, `listDocuments` de `src/api/client.js`, inchangés.
- Produit : rien qu'une vue.

- [ ] **Step 1: Passer `FileUpload` en mode avancé**

Dans le `<template>`, remplacer le bloc `FileUpload` par :

```vue
      <FileUpload
        ref="uploader"
        name="file"
        :accept="ACCEPTED_EXTENSIONS"
        multiple
        custom-upload
        auto
        choose-label="Déposer des documents"
        choose-icon="pi pi-upload"
        :disabled="busy"
        @uploader="upload"
      >
        <template #empty>
          <p class="upload-hint">Glissez vos fichiers ici, ou cliquez pour les choisir.</p>
        </template>
      </FileUpload>
```

`mode="basic"` disparaît : c'est le mode avancé qui porte la zone de glisser-déposer. `auto` +
`custom-upload` font que `@uploader` reçoit **tous** les fichiers déposés en une fois.

- [ ] **Step 2: Envoyer en séquence et nommer chaque refus**

Remplacer la fonction `upload` et l'état qui l'entoure. `errorMessage` (une chaîne) devient
`rejections` (une liste de `{ filename, message }`), et `duplicateId` devient `duplicateIds`, un
tableau — trois fichiers déposés peuvent produire trois doublons.

```js
const documents = ref([])
const loading = ref(false)
const busy = ref(false)
// One entry per file the server refused, so that a rejection names its file: a global message
// would lie as soon as one upload out of three is refused.
const rejections = ref([])
// Identifiers of the documents the server designated as duplicates of the last refused
// uploads: the matching rows are highlighted rather than left to be searched for.
const duplicateIds = ref([])

async function upload({ files }) {
  rejections.value = []
  duplicateIds.value = []
  busy.value = true
  try {
    // Sequentially: the route takes one file, and a burst of parallel uploads would race on
    // the duplicate check that precedes each write.
    for (const file of files) {
      await uploadOne(file)
    }
    // The 201 has no body: it is the list that gives the complete state of the base.
    await load()
  } finally {
    busy.value = false
    // Resets the component, so that the same files can be selected again.
    uploader.value?.clear()
  }
}

async function uploadOne(file) {
  try {
    await uploadDocument(auth.token, file)
  } catch (error) {
    if (error instanceof DuplicateDocumentError) {
      duplicateIds.value.push(error.existingDocumentId)
      rejections.value.push({ filename: file.name, message: error.message })
    } else if (error instanceof ValidationError) {
      // A single field in this form: its message is the file's message.
      rejections.value.push({ filename: file.name, message: error.errors.file ?? error.message })
    } else {
      await handle(error)
    }
  }
}
```

`handle` reste tel quel, à ceci près qu'il n'écrit plus dans `errorMessage` mais dans
`rejections` sans nom de fichier — remplacer sa dernière ligne par :

```js
  rejections.value.push({ filename: null, message: error.message })
```

`remove` et `rowClass` suivent le passage au pluriel :

```js
async function remove(document) {
  rejections.value = []
  busy.value = true
  try {
    await deleteDocument(auth.token, document.id)
    duplicateIds.value = duplicateIds.value.filter((id) => id !== document.id)
    await load()
  } catch (error) {
    await handle(error)
  } finally {
    busy.value = false
  }
}

function rowClass(document) {
  return duplicateIds.value.includes(document.id) ? 'table-duplicate-row' : ''
}
```

- [ ] **Step 3: Rendre les refus**

Remplacer le `<Message>` unique par la liste :

```vue
    <Message v-for="(rejection, index) in rejections" :key="index" severity="error">
      <span v-if="rejection.filename">« {{ rejection.filename }} » — </span>{{ rejection.message }}
    </Message>
```

- [ ] **Step 4: Vérifier**

```bash
gfront npm run test:unit
gfront npm run build
```

Attendu : verts. Puis, sur la pile lancée (`docker compose up`), déposer trois fichiers d'un coup
sur <http://localhost:8080/documents> — dont un `.png`, qui doit être refusé nommément pendant
que les deux autres entrent.

- [ ] **Step 5: Formater et committer**

```bash
make format-front
git add frontend/src/views/DocumentsView.vue
git commit
```

Message : `feat: l'écran des documents accepte plusieurs fichiers d'un coup`

---

### Task 3: Rafraîchir tant qu'un document bouge, et pas au-delà

**Files:**
- Modify: `frontend/src/views/DocumentsView.vue`

**Interfaces:**
- Consomme : `hasUnsettled` de la tâche 1.
- Produit : rien.

- [ ] **Step 1: Armer et désarmer l'horloge**

Ajouter à `<script setup>`, en important `onUnmounted` depuis `vue` et `hasUnsettled` depuis
`@/components/documentStatus` :

```js
// Two seconds, and only while something is still moving: a list that has settled must not keep
// asking. The clock is a `setTimeout` rearmed after each read, never a `setInterval` — a read
// slower than the interval would otherwise pile requests up.
const REFRESH_DELAY = 2000

let refreshTimer = null

function scheduleRefresh() {
  clearTimeout(refreshTimer)
  if (!hasUnsettled(documents.value)) {
    return
  }
  refreshTimer = setTimeout(load, REFRESH_DELAY)
}

onUnmounted(() => clearTimeout(refreshTimer))
```

- [ ] **Step 2: Rearmer après chaque lecture**

Dans `load`, appeler `scheduleRefresh()` une fois la liste reçue. Le `finally` ne convient pas :
un échec de lecture ne doit pas relancer une horloge qui échouerait en boucle.

```js
async function load() {
  loading.value = true
  try {
    documents.value = await listDocuments(auth.token)
    scheduleRefresh()
  } catch (error) {
    await handle(error)
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 3: Ne pas faire clignoter la liste**

`loading` pose le voile du `DataTable` : au rafraîchissement, il ferait clignoter la table toutes
les deux secondes. Le distinguer de la première lecture :

```js
const documents = ref([])
// Only the first read draws the table's veil: a refresh every two seconds must not make the
// list flicker.
const loading = ref(true)
```

et, dans `load`, remplacer `loading.value = true` par rien — la valeur initiale suffit — et
garder `loading.value = false` dans le `finally`.

- [ ] **Step 4: Vérifier**

```bash
gfront npm run test:unit
gfront npm run build
```

Puis, sur la pile lancée : déposer un PDF et regarder son statut passer de « En attente de
traitement » à « Prêt à être interrogé » **sans toucher au navigateur**. Ouvrir l'onglet réseau :
les appels à `/api/documents` doivent **cesser** une fois tous les documents prêts, et cesser en
quittant l'écran.

- [ ] **Step 5: Formater et committer**

```bash
make format-front
git add frontend/src/views/DocumentsView.vue
git commit
```

Message : `feat: la liste des documents se rafraîchit tant qu'un traitement est en cours`

---

### Task 4: Mettre à jour la documentation

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Récrire le paragraphe de `DocumentsView`**

Dans la section « Le flux du dépôt d'un document », le paragraphe qui commence par « Côté front,
`DocumentsView` … » dit encore « un `FileUpload` PrimeVue en mode `basic` ». Le corriger, et dire
les deux mécanismes nouveaux :

- le mode avancé de `FileUpload`, `multiple`, et l'envoi **en séquence** — une route par fichier,
  et une rafale parallèle courrait contre le contrôle de doublon qui précède chaque écriture ;
- un refus par fichier, nommé, plutôt qu'un message global ;
- le rafraîchissement toutes les 2 s **tant qu'un document n'est pas dans un statut terminal**,
  un `setTimeout` réarmé après chaque lecture réussie, désarmé à la sortie de l'écran — et le
  fait que `documentStatus.js` porte ce savoir hors du `.vue` pour qu'il soit testable, comme
  `answerSegments.js` et `sse.js`.

- [ ] **Step 2: Committer**

```bash
git add CLAUDE.md
git commit
```

Message : `docs: documente le dépôt multiple et le rafraîchissement de la liste`
