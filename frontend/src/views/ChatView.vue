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
  // `push` stores the raw object: Vue only wraps an element in a proxy when the array is
  // read. Mutating the reference we just pushed would never reach the screen, so the
  // streaming writes below go through the proxy the array hands back.
  const streamed = exchanges.value[exchanges.value.length - 1]
  await scrollToTheEnd()

  controller = new AbortController()
  try {
    await askAgent(auth.token, asked, {
      onToken: (fragment) => {
        streamed.text += fragment
      },
      onSources: (sources) => {
        streamed.sources = sources
      },
      signal: controller.signal,
    })
    streamed.state = 'complete'
  } catch (error) {
    streamed.state = 'failed'
    if (error.name === 'AbortError') {
      streamed.failure = 'Réponse interrompue.'
    } else if (await handle(error)) {
      return
    } else if (error instanceof ValidationError) {
      streamed.failure = error.errors.question ?? error.message
    } else {
      // The message comes from the server and is displayable as is.
      streamed.failure = error.message
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
        Posez une question : l'agent cherche dans vos documents et cite ce sur quoi il s'appuie.
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
