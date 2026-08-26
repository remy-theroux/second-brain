<script setup>
import { onMounted, ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Column from 'primevue/column'
import ConfirmPopup from 'primevue/confirmpopup'
import DataTable from 'primevue/datatable'
import FileUpload from 'primevue/fileupload'
import Message from 'primevue/message'
import PageTitle from '@/components/PageTitle.vue'
import {
  deleteDocument,
  DuplicateDocumentError,
  listDocuments,
  UnauthorizedError,
  uploadDocument,
  ValidationError,
} from '@/api/client'
import { useAuthStore } from '@/stores/auth'

// Filtre du sélecteur de fichiers, pas une règle : c'est le serveur qui refuse un format
// (415) et son message énonce la liste qui fait foi, construite depuis `DocumentFormat`.
// Cette copie ne sert qu'au confort du sélecteur et peut diverger sans qu'un test le voie —
// même nature de copie que `VERIFICATION_MESSAGES` dans LoginView — ADR-0022.
const ACCEPTED_EXTENSIONS = '.pdf,.md,.txt,.docx'

// Le statut voyage en code, comme tout ce que l'API sérialise d'une énumération ; le
// libellé est une affaire d'écran.
// Le libellé d'une énumération sérialisée par l'API est une affaire d'écran, pas une règle
// du serveur : ADR-0022 assume cette copie. Le motif d'échec, lui, vient du serveur et
// s'affiche tel quel — c'est un message d'erreur, et le front n'en réécrit aucun.
const STATUS_LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  FAILED: 'Traitement en échec',
}

const auth = useAuthStore()
const router = useRouter()
const confirm = useConfirm()
const uploader = useTemplateRef('uploader')

const documents = ref([])
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
// Identifiant du document que le serveur a désigné comme doublon du dernier dépôt refusé :
// la ligne correspondante est mise en évidence plutôt que laissée à chercher.
const duplicateId = ref(null)

// Le serveur fait autorité : un 401 sur n'importe quel appel déconnecte, quoi qu'en
// pense le navigateur. Toute autre panne s'affiche, sans déconnecter.
async function handle(error) {
  if (error instanceof UnauthorizedError) {
    auth.logout()
    await router.push({ name: 'login' })
    return
  }
  errorMessage.value = error.message
}

async function load() {
  loading.value = true
  try {
    documents.value = await listDocuments(auth.token)
  } catch (error) {
    await handle(error)
  } finally {
    loading.value = false
  }
}

async function upload({ files }) {
  errorMessage.value = ''
  duplicateId.value = null
  busy.value = true
  try {
    await uploadDocument(auth.token, files[0])
    // Le 201 n'a pas de corps : c'est la liste qui donne l'état complet de la base.
    await load()
  } catch (error) {
    if (error instanceof DuplicateDocumentError) {
      duplicateId.value = error.existingDocumentId
    }
    if (error instanceof ValidationError) {
      // Un seul champ dans ce formulaire : son message est le message global.
      errorMessage.value = error.errors.file ?? error.message
    } else {
      await handle(error)
    }
  } finally {
    busy.value = false
    // Remet le composant à zéro, pour que le même fichier puisse être re-sélectionné.
    uploader.value?.clear()
  }
}

function confirmRemoval(event, document) {
  confirm.require({
    target: event.currentTarget,
    message: `Supprimer « ${document.filename} » ?`,
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Annuler', severity: 'secondary', outlined: true },
    acceptProps: { label: 'Supprimer', severity: 'danger' },
    accept: () => remove(document),
  })
}

async function remove(document) {
  errorMessage.value = ''
  busy.value = true
  try {
    await deleteDocument(auth.token, document.id)
    if (duplicateId.value === document.id) {
      duplicateId.value = null
    }
    await load()
  } catch (error) {
    await handle(error)
  } finally {
    busy.value = false
  }
}

function rowClass(document) {
  return document.id === duplicateId.value ? 'table-duplicate-row' : ''
}

function formatDate(isoInstant) {
  return new Date(isoInstant).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })
}

onMounted(load)
</script>

<template>
  <section class="documents">
    <PageTitle>Documents</PageTitle>

    <div>
      <FileUpload
        ref="uploader"
        mode="basic"
        name="file"
        :accept="ACCEPTED_EXTENSIONS"
        custom-upload
        auto
        choose-label="Déposer un document"
        choose-icon="pi pi-upload"
        :disabled="busy"
        @uploader="upload"
      />
    </div>

    <Message v-if="errorMessage" severity="error">{{ errorMessage }}</Message>

    <ConfirmPopup />

    <DataTable :value="documents" :loading="loading" data-key="id" :row-class="rowClass">
      <template #empty>Aucun document pour l'instant.</template>
      <Column field="filename" header="Fichier" />
      <Column header="Statut">
        <template #body="{ data }">
          {{ STATUS_LABELS[data.status] ?? data.status }}
          <div v-if="data.errorMessage" class="document-error">{{ data.errorMessage }}</div>
        </template>
      </Column>
      <Column header="Déposé le">
        <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
      </Column>
      <Column class="table-actions">
        <template #body="{ data }">
          <Button
            type="button"
            icon="pi pi-trash"
            severity="danger"
            text
            rounded
            :disabled="busy"
            :aria-label="`Supprimer ${data.filename}`"
            @click="confirmRemoval($event, data)"
          />
        </template>
      </Column>
    </DataTable>
  </section>
</template>

<style scoped>
.document-error {
  margin-top: var(--sb-space-xs);
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.documents {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-md);
}
</style>
