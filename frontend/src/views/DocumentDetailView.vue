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

// Le serveur fait autorité : un 401 déconnecte, quoi qu'en pense le navigateur. Toute
// autre panne s'affiche — y compris le 404, dont le message vient du serveur.
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

// Le titre d'un bloc est décalé selon son niveau : c'est la seule chose qui rende visible
// la hiérarchie d'une suite volontairement plate (ADR-0024). Le décalage se compte en
// tokens du projet, jamais en `rem` nus.
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

      <!-- Le motif vient du serveur et s'affiche tel quel : le front ne réécrit aucun
           message d'erreur. -->
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

      <!-- Trois façons de n'avoir rien à montrer, trois phrases : « en attente » n'est pas
           un échec, et « typologie non lue » n'en est pas un non plus. -->
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

/* Le corps d'un bloc est déjà normalisé par le domaine : ses sauts de ligne sont
   significatifs, et un `pre-wrap` est la seule façon de ne pas les perdre. */
.block-text {
  margin: 0;
  white-space: pre-wrap;
}

.empty {
  margin: 0;
  color: var(--p-text-muted-color);
}
</style>
