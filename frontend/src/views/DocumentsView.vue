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
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import DownloadDocumentButton from '@/components/DownloadDocumentButton.vue'
import {
  deleteDocument,
  DuplicateDocumentError,
  listDocuments,
  UnauthorizedError,
  uploadDocument,
  ValidationError,
} from '@/api/client'
import { useAuthStore } from '@/stores/auth'

// Filter of the file picker, not a rule: it is the server that refuses a format (415) and
// its message states the list that prevails, built from `DocumentFormat`. This copy only
// serves the comfort of the picker and may diverge without a test seeing it — same nature
// of copy as `VERIFICATION_MESSAGES` in LoginView — ADR-0022.
const ACCEPTED_EXTENSIONS = '.pdf,.md,.txt,.docx'

const auth = useAuthStore()
const router = useRouter()
const confirm = useConfirm()
const uploader = useTemplateRef('uploader')

const documents = ref([])
const loading = ref(false)
const busy = ref(false)
const errorMessage = ref('')
// Identifier of the document the server designated as the duplicate of the last refused
// upload: the matching row is highlighted rather than left to be searched for.
const duplicateId = ref(null)

// The server prevails: a 401 on any call signs out, whatever the browser thinks. Any
// other failure is displayed, without signing out.
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
    // The 201 has no body: it is the list that gives the complete state of the base.
    await load()
  } catch (error) {
    if (error instanceof DuplicateDocumentError) {
      duplicateId.value = error.existingDocumentId
    }
    if (error instanceof ValidationError) {
      // A single field in this form: its message is the global message.
      errorMessage.value = error.errors.file ?? error.message
    } else {
      await handle(error)
    }
  } finally {
    busy.value = false
    // Resets the component, so that the same file can be selected again.
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
          <DocumentStatusTag :status="data.status" />
          <div v-if="data.errorMessage" class="document-error">{{ data.errorMessage }}</div>
        </template>
      </Column>
      <Column header="Déposé le">
        <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
      </Column>
      <Column class="table-actions">
        <template #body="{ data }">
          <DownloadDocumentButton
            :document-id="data.id"
            :filename="data.filename"
            :disabled="busy"
            @error="handle"
          />
          <Button
            type="button"
            icon="pi pi-eye"
            text
            rounded
            :aria-label="`Voir ${data.filename}`"
            @click="router.push({ name: 'document', params: { id: data.id } })"
          />
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
