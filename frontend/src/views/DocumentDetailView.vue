<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import PageTitle from '@/components/PageTitle.vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import { fetchDocument, UnauthorizedError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const document = ref(null)
const loading = ref(false)
const errorMessage = ref('')

// The server prevails: a 401 signs out, whatever the browser thinks. Any other failure is
// displayed — including the 404, whose message comes from the server.
async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    document.value = await fetchDocument(auth.token, route.params.id)
  } catch (error) {
    if (error instanceof UnauthorizedError) {
      auth.logout()
      await router.push({ name: 'login' })
      return
    }
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

function formatDate(isoInstant) {
  return new Date(isoInstant).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })
}

function formatSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} o`
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} Ko`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`
}

// A block's heading is indented according to its level: that is the only thing that makes
// visible the hierarchy of a deliberately flat sequence (ADR-0024). The indent is counted in
// project tokens, never in bare `rem`.
function headingIndent(level) {
  return { paddingLeft: `calc(var(--sb-space-lg) * ${Math.max(level - 1, 0)})` }
}

onMounted(load)
</script>

<template>
  <section class="document-detail">
    <div>
      <Button
        type="button"
        icon="pi pi-arrow-left"
        label="Documents"
        text
        @click="router.push({ name: 'documents' })"
      />
    </div>

    <ProgressSpinner v-if="loading" style="width: 2rem; height: 2rem" />

    <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

    <template v-if="document">
      <PageTitle>{{ document.filename }}</PageTitle>

      <dl class="meta">
        <div>
          <dt>Statut</dt>
          <dd><DocumentStatusTag :status="document.status" /></dd>
        </div>
        <div>
          <dt>Format</dt>
          <dd>{{ document.format }}</dd>
        </div>
        <div>
          <dt>Taille</dt>
          <dd>{{ formatSize(document.sizeBytes) }}</dd>
        </div>
        <div>
          <dt>Déposé le</dt>
          <dd>{{ formatDate(document.createdAt) }}</dd>
        </div>
      </dl>

      <!-- The reason comes from the server and is displayed as is: the front rewrites no
           error message. -->
      <Message v-if="document.errorMessage" severity="warn">{{ document.errorMessage }}</Message>

      <template v-if="document.extraction">
        <h2 class="section-title">Texte extrait</h2>
        <p class="summary">
          {{ document.extraction.blocks.length }} bloc(s) ·
          {{ document.extraction.characterCount }} caractères · extrait le
          {{ formatDate(document.extraction.extractedAt) }}
        </p>

        <article v-for="(block, index) in document.extraction.blocks" :key="index" class="block">
          <h3 v-if="block.heading" class="block-heading" :style="headingIndent(block.headingLevel)">
            {{ block.heading }}
          </h3>
          <p class="block-text">{{ block.text }}</p>
        </article>
      </template>

      <!-- Three ways of having nothing to show, three sentences: "pending" is not a
           failure, and "typology not rendered" is not one either. -->
      <p v-else-if="document.status === 'PENDING'" class="empty">
        Le texte de ce document n'a pas encore été extrait.
      </p>
      <p v-else-if="document.status === 'FAILED'" class="empty">
        Rien n'a pu être extrait de ce document.
      </p>
      <p v-else class="empty">Cette typologie de document n'a pas encore d'affichage.</p>
    </template>
  </section>
</template>

<style scoped>
.document-detail {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-md);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sb-space-lg);
  margin: 0;
}

.meta div {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
}

.meta dt {
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.meta dd {
  margin: 0;
}

.section-title {
  margin: 0;
  font-size: var(--sb-section-title-size);
}

.summary {
  margin: 0;
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.block {
  padding-top: var(--sb-space-md);
  border-top: 1px solid var(--p-content-border-color);
}

.block-heading {
  margin: 0 0 var(--sb-space-xs);
  font-size: var(--sb-section-title-size);
}

/* A block's body is already normalised by the domain: its line breaks are significant,
   and a `pre-wrap` is the only way not to lose them. */
.block-text {
  margin: 0;
  white-space: pre-wrap;
}

.empty {
  margin: 0;
  color: var(--p-text-muted-color);
}
</style>
