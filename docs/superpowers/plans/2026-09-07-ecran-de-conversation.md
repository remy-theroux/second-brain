# Écran de conversation — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** livrer l'écran `/chat` qui consomme `POST /api/chat`, affiche la réponse de l'agent
au fil de l'eau et laisse remonter de chaque citation `[n]` à l'extrait qui la porte.

**Architecture:** quatre couches empilées, chacune testable seule. Un lecteur de flux SSE
(`src/api/sse.js`) qui ne connaît que le format du fil ; une fonction `askAgent` dans
`src/api/client.js`, seul endroit qui connaisse la route, ses en-têtes et ses refus ; deux
composants partagés qui rendent une réponse et ses sources ; et la vue qui empile les
échanges dans l'état de la page. Rien n'est persisté : un F5 vide le fil.

**Tech Stack:** Vue 3 `<script setup>`, PrimeVue 4 (thème Aura, imports unitaires), pinia,
vue-router, Vitest + jsdom. Aucune dépendance nouvelle — `fetch` et `ReadableStream` sont
natifs.

**Spec:** ticket RAG-12 (<https://app.notion.com/p/3c0215c5e46e814fb5ecec4a843bd0d9>) et
`docs/superpowers/specs/2026-09-04-reponse-sourcee-design.md`, qui décrit le back consommé —
en particulier sa section « Quatre limites assumées », qui explique pourquoi le flux reste
silencieux.

## Global Constraints

- **Langue.** Le code, les commentaires et les libellés des `describe`/`it` sont en anglais.
  Les libellés d'écran et les messages affichés sont en français. Les messages d'erreur
  venant du serveur s'affichent tels quels, jamais réécrits.
- **Aucun Node sur l'hôte.** Toute commande passe par `gfront` (défini dans `CLAUDE.md`) :
  `gfront() { docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "$PWD/frontend":/app -w /app node:24-alpine "$@"; }`
- **`src/api/` est le seul endroit qui appelle `fetch`.** Un composant qui appelle une URL en
  direct est un bug.
- **Aucune couleur en dur.** Les couleurs passent par les tokens Aura (`--p-*`), les
  espacements et tailles par les tokens du projet (`--sb-*`). Un composant n'écrit jamais un
  `rem` nu.
- **Tout composant partagé nouveau apparaît dans `/design-system` dans le même commit.**
  Un composant absent de cette page n'est pas partagé.
- **Aucun test de rendu de composant** (ADR-0016) : pas de `@vue/test-utils`. On teste ce qui
  casse silencieusement — le parsing du flux, la traduction des refus, le découpage des
  citations, le garde de route.
- **Prettier décide du style** : `make format-front` avant chaque commit, et ne pas défaire
  son travail à la main.
- **Contrat du flux, à ne pas réinventer :** `token`* → `sources` → `done`, ou `error` seul.
  Un `error` remplace `sources` et `done` : rien ne suit.
- **Messages de commit en français**, préfixe conventionnel minuscule.

---

### Task 1: Lecteur de flux Server-Sent Events

Le format du fil, et rien d'autre. C'est le morceau qui casse en silence : une trame coupée
au mauvais endroit ou des lignes `data:` mal rejointes perdent du texte sans lever d'erreur.

**Files:**
- Create: `frontend/src/api/sse.js`
- Test: `frontend/src/api/sse.spec.js`

**Interfaces:**
- Consumes: rien.
- Produces: `readServerSentEvents(body)` — générateur asynchrone prenant le `ReadableStream`
  d'une réponse `fetch` et rendant des objets `{ event: string, data: string }` dans l'ordre
  d'arrivée.

**Deux pièges que le serveur impose, et qui sont la raison d'être de ce module :**

1. **Une réponse markdown contient des sauts de ligne.** Spring remplace chaque `\n` du
   contenu par `\ndata:` : une réponse sur trois lignes arrive en trois lignes `data:` dans
   une seule trame, à rejoindre par un `\n`. Un parseur qui ne lit que la première ligne
   `data:` perd tout le reste, sans bruit.
2. **On ne retire pas l'espace qui suit `data:`.** La spécification SSE dit d'en retirer un ;
   mais Spring écrit `data:` **sans** espace, donc l'espace observé appartient au texte. Un
   fragment ` jours` deviendrait `jours` et l'écran afficherait « Quatorzejours ». Le
   serveur est le nôtre et aucun intermédiaire ne réécrit les trames : on garde le contenu
   verbatim.

- [ ] **Step 1: Écrire les tests qui échouent**

Créer `frontend/src/api/sse.spec.js` :

```js
import { describe, expect, it } from 'vitest'
import { readServerSentEvents } from '@/api/sse'

// Turns text pieces into the byte stream a `fetch` response exposes. Each piece is one
// network chunk: that is how frame boundaries get to fall in awkward places.
function streamOf(...pieces) {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      pieces.forEach((piece) => controller.enqueue(encoder.encode(piece)))
      controller.close()
    },
  })
}

async function collect(stream) {
  const events = []
  for await (const event of readServerSentEvents(stream)) {
    events.push(event)
  }
  return events
}

describe('server-sent events reader', () => {
  it('reads the name and the payload of each event, in order', async () => {
    const events = await collect(
      streamOf('event:token\ndata:Quatorze\n\nevent:done\ndata:{"verdict":"GROUNDED"}\n\n'),
    )

    expect(events).toEqual([
      { event: 'token', data: 'Quatorze' },
      { event: 'done', data: '{"verdict":"GROUNDED"}' },
    ])
  })

  it('rejoins the several data lines of a multi-line payload', async () => {
    // The server splits a line break into a new `data:` line: rejoining without the
    // newline would run two paragraphs of the answer together.
    const events = await collect(streamOf('event:token\ndata:Premier\ndata:\ndata:Second\n\n'))

    expect(events).toEqual([{ event: 'token', data: 'Premier\n\nSecond' }])
  })

  it('keeps the space that opens a payload', async () => {
    // The server writes `data:` with no space of its own: the one seen here belongs to the
    // fragment. Stripping it would weld two words together.
    const events = await collect(streamOf('event:token\ndata: jours [1].\n\n'))

    expect(events).toEqual([{ event: 'token', data: ' jours [1].' }])
  })

  it('reassembles an event split across two network chunks', async () => {
    const events = await collect(streamOf('event:tok', 'en\ndata:Quat', 'orze\n\n'))

    expect(events).toEqual([{ event: 'token', data: 'Quatorze' }])
  })

  it('reassembles a multi-byte character split across two chunks', async () => {
    const encoder = new TextEncoder()
    const bytes = encoder.encode('event:token\ndata:été\n\n')
    const cut = bytes.indexOf(0xc3) + 1
    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(bytes.slice(0, cut))
        controller.enqueue(bytes.slice(cut))
        controller.close()
      },
    })

    expect(await collect(stream)).toEqual([{ event: 'token', data: 'été' }])
  })

  it('ignores a frame that carries no payload', async () => {
    // A comment or a keep-alive: nothing to hand to the caller.
    const events = await collect(streamOf(':ping\n\nevent:token\ndata:Quatorze\n\n'))

    expect(events).toEqual([{ event: 'token', data: 'Quatorze' }])
  })

  it('drops a truncated trailing frame rather than handing over half an event', async () => {
    const events = await collect(streamOf('event:token\ndata:Quatorze\n\nevent:done\ndata:{'))

    expect(events).toEqual([{ event: 'token', data: 'Quatorze' }])
  })
})
```

- [ ] **Step 2: Lancer les tests et vérifier qu'ils échouent**

Run: `gfront npx vitest run src/api/sse.spec.js`
Expected: FAIL — « Failed to resolve import "@/api/sse" ».

- [ ] **Step 3: Écrire le module**

Créer `frontend/src/api/sse.js` :

```js
// Server-Sent Events read from a `fetch` response body. `EventSource` is not an option: the
// chat route is a POST, and EventSource only ever does GET.

/**
 * Yields one `{ event, data }` per frame, in order. `data` is the payload verbatim: several
 * `data:` lines are rejoined with a newline, which is how the server sends a multi-line
 * answer.
 */
export async function* readServerSentEvents(body) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        // Whatever is left is a frame the server never finished: handing over half an
        // event would be worse than dropping it.
        return
      }
      // `stream: true` holds back a multi-byte character split across two chunks.
      buffer += decoder.decode(value, { stream: true })

      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const frame = parse(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        if (frame) {
          yield frame
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function parse(frame) {
  let event = 'message'
  const data = []

  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length)
    } else if (line.startsWith('data:')) {
      // No leading space is stripped, contrary to the specification: the server writes
      // `data:` without one, so a space seen here opens the fragment. Removing it would
      // weld the last word of a fragment onto the first of the next.
      data.push(line.slice('data:'.length))
    }
  }

  return data.length === 0 ? null : { event, data: data.join('\n') }
}
```

- [ ] **Step 4: Lancer les tests et vérifier qu'ils passent**

Run: `gfront npx vitest run src/api/sse.spec.js`
Expected: PASS — 7 tests.

- [ ] **Step 5: Formater et committer**

```bash
make format-front
git add frontend/src/api/sse.js frontend/src/api/sse.spec.js
git commit -m "feat: un lecteur de flux SSE pour le front"
```

---

### Task 2: `askAgent` — la conversation vue du client HTTP

**Files:**
- Create: rien
- Modify: `frontend/src/api/client.js` (ajout en fin de fichier + un import en tête)
- Test: `frontend/src/api/client.spec.js` (ajout d'un `describe` en fin de fichier)

**Interfaces:**
- Consumes: `readServerSentEvents(body)` de la tâche 1 ; `UnauthorizedError` et
  `ValidationError`, déjà exportés par `client.js`.
- Produces: `askAgent(token, question, { onToken, onSources, signal })` — promesse résolue
  sur le verdict (`'GROUNDED' | 'CONVERSATIONAL' | 'UNGROUNDED' | 'BUDGET_EXCEEDED'`) une
  fois la conversation close. `onToken(fragment)` est appelé à chaque fragment de texte,
  `onSources(sources)` une fois avec le tableau des sources citées, chacune de la forme
  `{ number, documentId, filename, position, heading, text }`.

- [ ] **Step 1: Écrire les tests qui échouent**

Ajouter à la fin de `frontend/src/api/client.spec.js` :

```js
describe('conversation', () => {
  // Builds the response a streaming route returns: a status, and a body that hands the
  // given SSE text over as bytes.
  function streamResponse(status, text) {
    const encoder = new TextEncoder()
    return {
      ok: status >= 200 && status < 300,
      status,
      body: new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode(text))
          controller.close()
        },
      }),
    }
  }

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts the question as JSON, bearing the token', async () => {
    fetch.mockResolvedValue(streamResponse(200, 'event:done\ndata:{"verdict":"CONVERSATIONAL"}\n\n'))

    await askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/chat')
    expect(options.method).toBe('POST')
    expect(options.headers.Authorization).toBe('Bearer jeton-abc')
    expect(JSON.parse(options.body)).toEqual({ question: 'Quel est le délai ?' })
  })

  it('hands over each fragment, then the sources, then the verdict', async () => {
    fetch.mockResolvedValue(
      streamResponse(
        200,
        'event:token\ndata:Quatorze\n\n' +
          'event:token\ndata: jours [1].\n\n' +
          'event:sources\ndata:[{"number":1,"documentId":"doc-1","filename":"rapport.pdf","position":3,"heading":"Rétractation","text":"Le délai est de quatorze jours."}]\n\n' +
          'event:done\ndata:{"verdict":"GROUNDED"}\n\n',
      ),
    )
    const fragments = []
    let received = null

    const verdict = await askAgent('jeton-abc', 'Quel est le délai ?', {
      onToken: (fragment) => fragments.push(fragment),
      onSources: (sources) => {
        received = sources
      },
    })

    expect(fragments.join('')).toBe('Quatorze jours [1].')
    expect(received).toHaveLength(1)
    expect(received[0].filename).toBe('rapport.pdf')
    expect(verdict).toBe('GROUNDED')
  })

  it('raises the message the server put in its error event', async () => {
    fetch.mockResolvedValue(
      streamResponse(200, 'event:error\ndata:{"message":"La conversation a échoué."}\n\n'),
    )

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow('La conversation a échoué.')
  })

  it('raises when the stream stops before the end of the conversation', async () => {
    // A proxy that cuts, a server that dies: the events simply stop. Resolving here would
    // leave a half-written answer looking finished.
    fetch.mockResolvedValue(streamResponse(200, 'event:token\ndata:Quatorze\n\n'))

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow("La conversation s'est interrompue avant la fin de la réponse.")
  })

  it('translates a 401 into an expired session', async () => {
    fetch.mockResolvedValue({ ok: false, status: 401, json: () => Promise.resolve(null) })

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow(UnauthorizedError)
  })

  it('translates a 422 into a refusal on the question field', async () => {
    fetch.mockResolvedValue(
      jsonResponse(422, { errors: { question: 'La question ne peut pas être vide.' } }),
    )

    try {
      await askAgent('jeton-abc', '   ', { onToken: () => {}, onSources: () => {} })
      expect.unreachable('the refusal should have been raised')
    } catch (error) {
      expect(error).toBeInstanceOf(ValidationError)
      expect(error.errors).toEqual({ question: 'La question ne peut pas être vide.' })
    }
  })

  it('does not replace the failure with a syntax error when the body is not JSON', async () => {
    fetch.mockResolvedValue({
      ok: false,
      status: 502,
      json: () => Promise.reject(new SyntaxError('Unexpected token <')),
    })

    await expect(
      askAgent('jeton-abc', 'Quel est le délai ?', { onToken: () => {}, onSources: () => {} }),
    ).rejects.toThrow("La conversation n'a pas pu démarrer.")
  })
})
```

Ajouter `askAgent` à l'import en tête du fichier de test :

```js
import {
  askAgent,
  deleteDocument,
  DuplicateDocumentError,
  fetchDocument,
  listDocuments,
  register,
  UnauthorizedError,
  uploadDocument,
  ValidationError,
} from '@/api/client'
```

- [ ] **Step 2: Lancer les tests et vérifier qu'ils échouent**

Run: `gfront npx vitest run src/api/client.spec.js`
Expected: FAIL — « askAgent is not a function » (les tests déjà présents restent verts).

- [ ] **Step 3: Écrire l'implémentation**

En tête de `frontend/src/api/client.js`, sous le commentaire d'en-tête, ajouter :

```js
import { readServerSentEvents } from '@/api/sse'
```

Puis, à la fin du fichier :

```js
/**
 * Asks the agent a question and consumes its stream, resolving on the verdict once the
 * conversation is closed.
 *
 * `POST` + SSE cannot be read with `EventSource`, which only does `GET`: the body is read by
 * hand. `signal` aborts the request, and that abort is what stops generation server-side.
 */
export async function askAgent(token, question, { onToken, onSources, signal }) {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ question }),
    signal,
  })

  if (response.status === 401) {
    throw new UnauthorizedError()
  }
  if (!response.ok) {
    // The body is not guaranteed to be JSON (proxy down, HTML 502…): a parse that fails
    // must not replace the business message with a syntax error.
    const payload = await response.json().catch(() => null)

    if (response.status === 422) {
      throw new ValidationError(payload?.errors ?? {})
    }
    throw new Error(payload?.message ?? "La conversation n'a pas pu démarrer.")
  }

  let verdict = null

  for await (const { event, data } of readServerSentEvents(response.body)) {
    if (event === 'token') {
      onToken(data)
    } else if (event === 'sources') {
      onSources(JSON.parse(data))
    } else if (event === 'done') {
      verdict = JSON.parse(data).verdict
    } else if (event === 'error') {
      // `error` comes INSTEAD of `sources` and `done`: nothing else will follow.
      throw new Error(JSON.parse(data).message)
    }
  }

  if (verdict === null) {
    throw new Error("La conversation s'est interrompue avant la fin de la réponse.")
  }
  return verdict
}
```

- [ ] **Step 4: Lancer les tests et vérifier qu'ils passent**

Run: `gfront npm run test:unit`
Expected: PASS — toute la suite, y compris les 7 nouveaux tests de conversation.

- [ ] **Step 5: Formater et committer**

```bash
make format-front
git add frontend/src/api/client.js frontend/src/api/client.spec.js
git commit -m "feat: le client front sait interroger l'agent et lire son flux"
```

---

### Task 3: Rendu d'une réponse et de ses sources

Deux composants partagés et la logique pure qu'ils rendent. Le découpage du texte vit hors du
`.vue` parce qu'il casse silencieusement — une expression régulière trop gourmande avale du
texte sans que rien ne le signale — et qu'un `.vue` ne se teste pas ici (ADR-0016).

**Files:**
- Create: `frontend/src/components/answerSegments.js`
- Create: `frontend/src/components/AnswerText.vue`
- Create: `frontend/src/components/AnswerSources.vue`
- Modify: `frontend/src/views/DesignSystemView.vue`
- Test: `frontend/src/components/answerSegments.spec.js`

**Interfaces:**
- Consumes: la forme d'une source produite par la tâche 2 —
  `{ number, documentId, filename, position, heading, text }`.
- Produces:
  - `answerSegments(text, sources)` → tableau de `{ text }` (prose) et
    `{ number, text }` (citation dont la source est connue).
  - `<AnswerText :text :sources @citation="(number) => …" />`
  - `<AnswerSources :sources :opened @update:opened="(numbers) => …" />` — `opened` est un
    tableau de numéros ; le composant ne retient rien, c'est l'appelant qui tient l'état.

- [ ] **Step 1: Écrire le test qui échoue**

Créer `frontend/src/components/answerSegments.spec.js` :

```js
import { describe, expect, it } from 'vitest'
import { answerSegments } from '@/components/answerSegments'

const SOURCES = [
  { number: 1, documentId: 'doc-1', filename: 'rapport.pdf', position: 3, heading: 'A', text: 'x' },
  { number: 2, documentId: 'doc-2', filename: 'notes.md', position: 0, heading: 'B', text: 'y' },
]

describe('answer segments', () => {
  it('turns a known citation into a segment of its own', () => {
    expect(answerSegments('Quatorze jours [1].', SOURCES)).toEqual([
      { text: 'Quatorze jours ' },
      { number: 1, text: '[1]' },
      { text: '.' },
    ])
  })

  it('keeps the whole text when no citation is present', () => {
    expect(answerSegments('Bonjour.', SOURCES)).toEqual([{ text: 'Bonjour.' }])
  })

  it('leaves a marker whose source is unknown as prose', () => {
    // Two cases meet here: the sources arrive after the text, so everything is unknown while
    // it streams; and a real document often carries its own footnote calls, which the model
    // copies. Neither must produce a link to nothing.
    expect(answerSegments('Voir [7] et [1].', SOURCES)).toEqual([
      { text: 'Voir [7] et ' },
      { number: 1, text: '[1]' },
      { text: '.' },
    ])
  })

  it('handles several citations in a row', () => {
    expect(answerSegments('[1][2]', SOURCES)).toEqual([
      { number: 1, text: '[1]' },
      { number: 2, text: '[2]' },
    ])
  })

  it('returns nothing for an empty text', () => {
    expect(answerSegments('', SOURCES)).toEqual([])
  })

  it('preserves the line breaks of the text it cuts', () => {
    expect(answerSegments('Premier [1].\n\nSecond.', SOURCES)).toEqual([
      { text: 'Premier ' },
      { number: 1, text: '[1]' },
      { text: '.\n\nSecond.' },
    ])
  })
})
```

- [ ] **Step 2: Lancer le test et vérifier qu'il échoue**

Run: `gfront npx vitest run src/components/answerSegments.spec.js`
Expected: FAIL — « Failed to resolve import "@/components/answerSegments" ».

- [ ] **Step 3: Écrire la logique pure**

Créer `frontend/src/components/answerSegments.js` :

```js
// A citation is written `[n]` and nothing else. The server's CitationPolicy owns that syntax
// on its side; this is its only counterpart on the screen.
const CITATION = /\[(\d+)\]/g

/**
 * Cuts an answer into what a screen has to render: `{ text }` for prose, `{ number, text }`
 * for a citation whose source is known. A marker with no matching source stays prose — the
 * sources arrive after the text, and a link to nothing is worse than a raw marker.
 */
export function answerSegments(text, sources) {
  const known = new Set(sources.map((source) => source.number))
  const segments = []
  let cursor = 0

  for (const match of text.matchAll(CITATION)) {
    const number = Number(match[1])
    if (!known.has(number)) {
      continue
    }
    if (match.index > cursor) {
      segments.push({ text: text.slice(cursor, match.index) })
    }
    segments.push({ number, text: match[0] })
    cursor = match.index + match[0].length
  }

  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor) })
  }
  return segments
}
```

- [ ] **Step 4: Lancer le test et vérifier qu'il passe**

Run: `gfront npx vitest run src/components/answerSegments.spec.js`
Expected: PASS — 6 tests.

- [ ] **Step 5: Écrire `AnswerText.vue`**

Créer `frontend/src/components/AnswerText.vue` :

```vue
<script setup>
import { computed } from 'vue'
import { answerSegments } from '@/components/answerSegments'

const props = defineProps({
  text: { type: String, required: true },
  sources: { type: Array, default: () => [] },
})

defineEmits(['citation'])

const segments = computed(() => answerSegments(props.text, props.sources))
</script>

<template>
  <p class="answer-text">
    <template v-for="(segment, index) in segments" :key="index">
      <button
        v-if="segment.number"
        type="button"
        class="answer-citation"
        :aria-label="`Voir la source ${segment.number}`"
        @click="$emit('citation', segment.number)"
      >
        {{ segment.text }}
      </button>
      <template v-else>{{ segment.text }}</template>
    </template>
  </p>
</template>

<style scoped>
.answer-text {
  margin: 0;
  /* The answer's line breaks are the only structure it has: they are kept as sent. */
  white-space: pre-wrap;
}

.answer-citation {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--p-primary-color);
  cursor: pointer;
}

.answer-citation:hover {
  text-decoration: underline;
}
</style>
```

- [ ] **Step 6: Écrire `AnswerSources.vue`**

Créer `frontend/src/components/AnswerSources.vue` :

```vue
<script setup>
import { RouterLink } from 'vue-router'

const props = defineProps({
  sources: { type: Array, required: true },
  // Numbers of the sources shown unfolded. The component holds nothing: following a
  // citation in the text opens a source, so the caller owns that state.
  opened: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:opened'])

function toggle(number) {
  emit(
    'update:opened',
    props.opened.includes(number)
      ? props.opened.filter((candidate) => candidate !== number)
      : [...props.opened, number],
  )
}
</script>

<template>
  <section v-if="sources.length" class="answer-sources">
    <h2 class="answer-sources-title">Sources</h2>

    <article v-for="source in sources" :key="source.number" class="answer-source">
      <button
        type="button"
        class="answer-source-header"
        :aria-expanded="opened.includes(source.number)"
        @click="toggle(source.number)"
      >
        <span :class="opened.includes(source.number) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'" />
        <span class="answer-source-number">[{{ source.number }}]</span>
        <span>{{ source.filename }}</span>
        <span v-if="source.heading" class="answer-source-heading">— {{ source.heading }}</span>
      </button>

      <div v-if="opened.includes(source.number)" class="answer-source-body">
        <blockquote class="answer-source-excerpt">{{ source.text }}</blockquote>
        <RouterLink :to="{ name: 'document', params: { id: source.documentId } }">
          Voir le document
        </RouterLink>
      </div>
    </article>
  </section>
</template>

<style scoped>
.answer-sources {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
  padding-top: var(--sb-space-sm);
  border-top: 1px solid var(--p-content-border-color);
}

.answer-sources-title {
  margin: 0;
  font-size: var(--sb-text-small);
  text-transform: uppercase;
  color: var(--p-text-muted-color);
}

.answer-source-header {
  display: flex;
  align-items: center;
  gap: var(--sb-space-xs);
  width: 100%;
  padding: var(--sb-space-xs) 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--p-text-color);
  text-align: left;
  cursor: pointer;
}

.answer-source-number {
  color: var(--p-primary-color);
}

.answer-source-heading {
  color: var(--p-text-muted-color);
}

.answer-source-body {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
  padding: 0 0 var(--sb-space-sm) var(--sb-space-lg);
}

.answer-source-excerpt {
  margin: 0;
  padding-left: var(--sb-space-sm);
  border-left: 2px solid var(--p-content-border-color);
  color: var(--p-text-muted-color);
  white-space: pre-wrap;
}
</style>
```

- [ ] **Step 7: Ajouter les deux composants au design system**

Dans `frontend/src/views/DesignSystemView.vue`, ajouter aux imports du `<script setup>` :

```js
import AnswerText from '@/components/AnswerText.vue'
import AnswerSources from '@/components/AnswerSources.vue'
```

Ajouter, à côté des autres jeux de données du `<script setup>` :

```js
const ANSWER_SOURCES = [
  {
    number: 1,
    documentId: 'doc-1',
    filename: 'rapport.pdf',
    position: 3,
    heading: 'Rétractation',
    text: 'Le délai de rétractation est de quatorze jours à compter de la réception.',
  },
  {
    number: 2,
    documentId: 'doc-2',
    filename: 'conditions-generales.md',
    position: 0,
    heading: 'Remboursement',
    text: 'Le remboursement intervient au plus tard trente jours après le retour.',
  },
]

const openedSources = ref([1])
```

Ajouter deux sections avant la fermeture de `</main>` dans le `<template>` :

```html
    <section>
      <h2>Réponse de l'agent — AnswerText</h2>
      <p class="muted">
        Les <code>[n]</code> dont la source est connue deviennent cliquables ; les autres restent
        du texte. Pendant que la réponse s'écrit, aucune source n'est encore arrivée : c'est la
        deuxième forme qu'on voit.
      </p>
      <div class="stack">
        <AnswerText
          text="Le délai de rétractation est de quatorze jours [1], et le remboursement intervient sous trente jours [2]. La note [7] du document n'est pas une source."
          :sources="ANSWER_SOURCES"
        />
        <AnswerText text="Le délai de rétractation est de quatorze jours [1]." :sources="[]" />
      </div>
    </section>

    <section>
      <h2>Sources d'une réponse — AnswerSources</h2>
      <p class="muted">
        Une entrée par source citée, dépliable. Suivre un <code>[n]</code> dans le texte ouvre
        l'entrée correspondante : l'état est tenu par l'écran, pas par le composant.
      </p>
      <AnswerSources
        :sources="ANSWER_SOURCES"
        :opened="openedSources"
        @update:opened="openedSources = $event"
      />
    </section>
```

- [ ] **Step 8: Vérifier que le front compile et que la suite reste verte**

Run: `gfront npm run build && gfront npm run test:unit`
Expected: build réussi, tous les tests au vert.

Puis regarder la page à l'œil : `docker compose up -d` si la pile est arrêtée, et ouvrir
<http://localhost:8080/design-system>. Vérifier que les `[1]` et `[2]` sont cliquables, que
`[7]` ne l'est pas, qu'une entrée de source se plie et se déplie, et que tout tient en thème
clair comme en thème sombre.

- [ ] **Step 9: Formater et committer**

```bash
make format-front
git add frontend/src/components/answerSegments.js frontend/src/components/answerSegments.spec.js \
        frontend/src/components/AnswerText.vue frontend/src/components/AnswerSources.vue \
        frontend/src/views/DesignSystemView.vue
git commit -m "feat: une réponse sourcée se rend avec ses citations cliquables"
```

---

### Task 4: L'écran de conversation

**Files:**
- Create: `frontend/src/views/ChatView.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/AuthenticatedLayout.vue`
- Test: `frontend/src/router/index.spec.js`

**Interfaces:**
- Consumes: `askAgent`, `listDocuments`, `UnauthorizedError`, `ValidationError` de
  `@/api/client` ; `AnswerText` et `AnswerSources` de la tâche 3 ; `useAuthStore`.
- Produces: la route nommée `chat`, sur `/chat`, `meta: { requiresAuth: true }`.

- [ ] **Step 1: Écrire les tests de route qui échouent**

Ajouter à la fin de `frontend/src/router/index.spec.js` :

```js
describe('conversation page', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('sends back to the login when no token is held', async () => {
    const router = createTestRouter()

    await router.push('/chat')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('lets the conversation be reached with a valid token', async () => {
    authenticate()
    const router = createTestRouter()

    await router.push('/chat')

    expect(router.currentRoute.value.name).toBe('chat')
  })
})
```

- [ ] **Step 2: Lancer les tests et vérifier qu'ils échouent**

Run: `gfront npx vitest run src/router/index.spec.js`
Expected: FAIL — les **deux** tests. Aucune route ne correspond à `/chat`, donc `to.meta` est
vide : le garde laisse passer, et `currentRoute.value.name` vaut `undefined` au lieu de
`login` puis de `chat`.

- [ ] **Step 3: Écrire la vue**

Créer `frontend/src/views/ChatView.vue` :

```vue
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import PageTitle from '@/components/PageTitle.vue'
import AnswerText from '@/components/AnswerText.vue'
import AnswerSources from '@/components/AnswerSources.vue'
import { askAgent, listDocuments, UnauthorizedError, ValidationError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const thread = useTemplateRef('thread')

const question = ref('')
const exchanges = ref([])
const errorMessage = ref('')
// Null as long as the base has not been read: neither an invitation nor its absence is
// shown on a guess.
const searchable = ref(null)

let controller = null
let nextId = 0

const pending = computed(() => exchanges.value.some((exchange) => exchange.state === 'streaming'))

// The agent only searches what is indexed: a document uploaded but not yet vectorised is not
// searchable, and inviting nobody to upload would be a lie on a base full of pending files.
async function checkTheBase() {
  try {
    const documents = await listDocuments(auth.token)
    searchable.value = documents.some((document) => document.status === 'READY')
  } catch (error) {
    if (!(await handle(error))) {
      // `searchable` stays null: neither the invitation nor its absence is shown on a guess.
      errorMessage.value = error.message
    }
  }
}

// The server prevails: a 401 on any call signs out, whatever the browser thinks.
async function handle(error) {
  if (error instanceof UnauthorizedError) {
    auth.logout()
    await router.push({ name: 'login' })
    return true
  }
  return false
}

async function ask() {
  const asked = question.value.trim()
  if (!asked || pending.value) {
    return
  }
  question.value = ''
  errorMessage.value = ''

  const exchange = {
    id: (nextId += 1),
    question: asked,
    text: '',
    sources: [],
    opened: [],
    state: 'streaming',
    failure: '',
  }
  exchanges.value.push(exchange)
  await scrollToTheEnd()

  controller = new AbortController()
  try {
    await askAgent(auth.token, asked, {
      onToken: (fragment) => {
        exchange.text += fragment
      },
      onSources: (sources) => {
        exchange.sources = sources
      },
      signal: controller.signal,
    })
    exchange.state = 'complete'
  } catch (error) {
    exchange.state = 'failed'
    if (error.name === 'AbortError') {
      exchange.failure = 'Réponse interrompue.'
    } else if (await handle(error)) {
      return
    } else if (error instanceof ValidationError) {
      exchange.failure = error.errors.question ?? error.message
    } else {
      // The message comes from the server and is displayable as is.
      exchange.failure = error.message
    }
  } finally {
    controller = null
    await scrollToTheEnd()
  }
}

// Aborting the request is what stops generation server-side: without it the model keeps
// running its budget out for an answer nobody will read.
function interrupt() {
  controller?.abort()
}

onBeforeUnmount(interrupt)

async function scrollToTheEnd() {
  await nextTick()
  thread.value?.scrollTo({ top: thread.value.scrollHeight })
}

function openCitation(exchange, number) {
  if (!exchange.opened.includes(number)) {
    exchange.opened = [...exchange.opened, number]
  }
}

onMounted(checkTheBase)
</script>

<template>
  <section class="chat">
    <PageTitle>Conversation</PageTitle>

    <Message v-if="searchable === false" severity="info">
      Aucun document n'est encore interrogeable.
      <RouterLink :to="{ name: 'documents' }">Déposez-en un</RouterLink> pour commencer.
    </Message>

    <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

    <div ref="thread" class="thread">
      <p v-if="exchanges.length === 0" class="thread-empty">
        Posez une question : l'agent cherche dans vos documents et cite ce sur quoi il
        s'appuie.
      </p>

      <article v-for="exchange in exchanges" :key="exchange.id" class="exchange">
        <p class="question">{{ exchange.question }}</p>

        <div class="answer">
          <!--
            The stream stays silent until the first valid citation, and a conversational
            answer arrives in a single block at the end: this indicator is the only feedback
            for tens of seconds. See the spec, "Quatre limites assumées".
          -->
          <p v-if="exchange.state === 'streaming' && !exchange.text" class="waiting">
            <span class="pi pi-spin pi-spinner" /> L'agent cherche dans vos documents…
          </p>

          <AnswerText
            v-if="exchange.text"
            :text="exchange.text"
            :sources="exchange.sources"
            @citation="openCitation(exchange, $event)"
          />

          <AnswerSources
            :sources="exchange.sources"
            :opened="exchange.opened"
            @update:opened="exchange.opened = $event"
          />

          <Message v-if="exchange.failure" severity="error">{{ exchange.failure }}</Message>
        </div>
      </article>
    </div>

    <form class="composer" @submit.prevent="ask">
      <Textarea
        v-model="question"
        rows="2"
        auto-resize
        placeholder="Posez votre question…"
        aria-label="Votre question"
        :disabled="pending"
        @keydown.enter.exact.prevent="ask"
      />
      <Button
        v-if="pending"
        type="button"
        label="Interrompre"
        icon="pi pi-times"
        severity="secondary"
        @click="interrupt"
      />
      <Button v-else type="submit" label="Envoyer" icon="pi pi-send" :disabled="!question.trim()" />
    </form>
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-md);
  height: 100%;
}

.thread {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-lg);
}

.thread-empty {
  margin: 0;
  color: var(--p-text-muted-color);
}

.exchange {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-sm);
}

.question {
  margin: 0;
  font-weight: bold;
}

.answer {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-sm);
  padding-left: var(--sb-space-md);
  border-left: 2px solid var(--p-content-border-color);
}

.waiting {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--sb-space-xs);
  color: var(--p-text-muted-color);
}

.composer {
  display: flex;
  align-items: flex-start;
  gap: var(--sb-space-sm);
}

.composer textarea {
  flex: 1;
}
</style>
```

- [ ] **Step 4: Déclarer la route**

Dans `frontend/src/router/index.js`, ajouter l'import :

```js
import ChatView from '@/views/ChatView.vue'
```

et la route, après celle des documents :

```js
  { path: '/chat', name: 'chat', component: ChatView, meta: { requiresAuth: true } },
```

- [ ] **Step 5: Ajouter l'entrée dans la barre latérale**

Dans `frontend/src/components/AuthenticatedLayout.vue`, compléter `menuItems` :

```js
const menuItems = [
  { label: 'Accueil', icon: 'pi pi-home', route: { name: 'home' } },
  { label: 'Documents', icon: 'pi pi-file', route: { name: 'documents' } },
  { label: 'Conversation', icon: 'pi pi-comments', route: { name: 'chat' } },
]
```

- [ ] **Step 6: Lancer les tests et vérifier qu'ils passent**

Run: `gfront npm run test:unit && gfront npm run build`
Expected: tous les tests au vert, build réussi.

- [ ] **Step 7: Essayer l'écran pour de vrai**

```bash
docker compose up -d
docker compose logs -f app
```

Sur <http://localhost:8080/chat>, avec un compte dont au moins un document est `Prêt à être
interrogé` :

1. Poser une question à laquelle un document répond. Vérifier que l'indicateur d'attente
   apparaît immédiatement, que le texte arrive ensuite, que les `[n]` sont cliquables une
   fois les sources reçues, et qu'un clic déplie la bonne source.
2. Vérifier que « Voir le document » ouvre bien `/documents/:id`.
3. Poser une question hors sujet (« Quelle est la capitale de l'Australie ? ») : l'aveu
   d'ignorance doit s'afficher, sans source.
4. Poser une question, puis cliquer « Interrompre » : « Réponse interrompue. » s'affiche, et
   `docker compose logs app` montre la ligne « The client closed its connection ».
5. Arrêter Ollama (`docker compose stop ollama`), reposer une question : un message d'erreur
   lisible s'affiche, pas une page figée. Puis `docker compose start ollama`.

- [ ] **Step 8: Formater et committer**

```bash
make format-front
git add frontend/src/views/ChatView.vue frontend/src/router/index.js \
        frontend/src/router/index.spec.js frontend/src/components/AuthenticatedLayout.vue
git commit -m "feat: l'écran de conversation interroge l'agent et montre ses sources"
```

---

## Ce qui reste hors de ce plan

- **L'historique persisté.** Les traces vivent dans `knowledge_agent_runs` mais aucune route
  ne les relit ; un F5 vide le fil, et c'est assumé.
- **Les questions de suivi.** `POST /api/chat` ne reçoit qu'une question isolée : l'agent n'a
  aucune mémoire du tour précédent. Le fil à l'écran ne doit pas laisser croire l'inverse.
- **Le heartbeat SSE.** Le flux peut rester silencieux plusieurs dizaines de secondes ; un
  envoi périodique demande un second thread sur un `SseEmitter` qui n'est pas thread-safe en
  écriture concurrente. Ticket à part.
- **La réindexation d'un document resté `EXTRACTED`** — RAG-7.
