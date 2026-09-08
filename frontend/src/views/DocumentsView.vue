<script setup>
import { computed, onMounted, onUnmounted, ref, useTemplateRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Column from 'primevue/column'
import ConfirmPopup from 'primevue/confirmpopup'
import DataTable from 'primevue/datatable'
import FileUpload from 'primevue/fileupload'
import Message from 'primevue/message'
import PageTitle from '@/components/PageTitle.vue'
import DocumentStatusTag from '@/components/DocumentStatusTag.vue'
import DriveFolderPicker from '@/components/DriveFolderPicker.vue'
import DriveSourceCard from '@/components/DriveSourceCard.vue'
import { hasUnsettled } from '@/components/documentStatus'
import { driveMessage } from '@/components/driveMessages'
import DownloadDocumentButton from '@/components/DownloadDocumentButton.vue'
import {
  deleteDocument,
  disconnectDrive,
  DuplicateDocumentError,
  fetchDriveConnection,
  importWatchedFolder,
  listDocuments,
  listWatchedFolders,
  startDriveAuthorization,
  UnauthorizedError,
  unwatchDriveFolder,
  uploadDocument,
  ValidationError,
  watchDriveFolder,
} from '@/api/client'
import { useAuthStore } from '@/stores/auth'

// The list that prevails is the server's, built from `DocumentFormat` and stated by its 415 —
// this is a copy, of the same nature as `VERIFICATION_MESSAGES` in LoginView (ADR-0022). But in
// advanced mode it is no longer only the picker's filter: `FileUpload` refuses a dropped file
// on it, so a divergence now withholds a format the server would have accepted.
const ACCEPTED_EXTENSIONS = '.pdf,.md,.txt,.docx'

// `FileUpload` has this message in English and hard-coded — it is a prop, not a locale entry,
// so `primelocale/fr` does not carry it. `{0}` is the file, `{1}` the accepted extensions.
const INVALID_FORMAT_MESSAGE = "« {0} » n'est pas d'un format accepté. Formats acceptés : {1}."

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const confirm = useConfirm()
const uploader = useTemplateRef('uploader')

// Two seconds, and only while something is still moving: a list that has settled must not keep
// asking. The clock is a `setTimeout` rearmed after each read, never a `setInterval` — a read
// slower than the interval would otherwise pile requests up.
const REFRESH_DELAY = 2000

// Nothing is unsettled when an import is requested: the walk has not even started when the 202
// leaves. The clock is armed for a bounded number of cycles, so that the first documents and
// the outcome of the walk appear without an F5.
const IMPORT_REFRESHES = 15

let refreshTimer = null
let remainingImportRefreshes = 0
// A `clearTimeout` on an already-fired timer clears nothing: a read in flight when the screen
// closes would resolve and rearm the clock, on a component nobody watches any more.
let mounted = true

const documents = ref([])
// Only the first read draws the table's veil: a refresh every two seconds must not make the
// list flicker.
const loading = ref(true)
const busy = ref(false)
// One entry per file the server refused, so that a rejection names its file: a global message
// would lie as soon as one upload out of three is refused.
const rejections = ref([])
// Identifiers of the documents the server designated as duplicates of the last refused
// uploads: the matching rows are highlighted rather than left to be searched for.
const duplicateIds = ref([])
// The outcome of a Google authorization, read once from the URL then erased from it.
const driveOutcome = ref(null)
// `null` while no Drive is connected, which is the ordinary state of a new account.
const driveConnection = ref(null)
const watchedFolders = ref([])
const picking = ref(false)

const needsReconnection = computed(() => driveConnection.value?.status === 'NEEDS_RECONNECTION')

// The server prevails: a 401 on any call signs out, whatever the browser thinks. Answers
// whether it did, so that a caller who shows the message itself does not show it twice.
async function signOutIfRejected(error) {
  if (!(error instanceof UnauthorizedError)) {
    return false
  }
  auth.logout()
  await router.push({ name: 'login' })
  return true
}

// Any failure other than a lost session is displayed, without signing out.
async function handle(error) {
  if (await signOutIfRejected(error)) {
    return
  }
  rejections.value.push({ filename: null, message: error.message })
}

// The code is read at mount, then removed from the URL: left there, an F5 would replay the
// message of a connection made ten minutes earlier, and it would stay in the browser history.
function readDriveOutcome() {
  if (route.query.drive === undefined) {
    return
  }
  driveOutcome.value = driveMessage(route.query.drive)

  const query = { ...route.query }
  delete query.drive
  router.replace({ query })
}

function scheduleRefresh() {
  clearTimeout(refreshTimer)
  if (!mounted) {
    return
  }
  if (remainingImportRefreshes > 0) {
    remainingImportRefreshes -= 1
  } else if (!hasUnsettled(documents.value)) {
    return
  }
  refreshTimer = setTimeout(load, REFRESH_DELAY)
}

async function load() {
  try {
    documents.value = await listDocuments(auth.token)
    // The folders travel with the documents: an import in flight moves their count and their
    // outcome at the same pace as the list.
    if (driveConnection.value) {
      watchedFolders.value = await listWatchedFolders(auth.token)
    }
    scheduleRefresh()
  } catch (error) {
    await handle(error)
  } finally {
    loading.value = false
  }
}

async function loadDrive() {
  try {
    driveConnection.value = await fetchDriveConnection(auth.token)
    watchedFolders.value = driveConnection.value ? await listWatchedFolders(auth.token) : []
  } catch (error) {
    await handle(error)
  }
}

// Also the way back: an authorization that no longer holds is repaired by the same round trip.
async function connect() {
  busy.value = true
  try {
    // The browser leaves for Google: on the nominal path, nothing below this line runs.
    window.location.href = await startDriveAuthorization(auth.token)
  } catch (error) {
    busy.value = false
    await handle(error)
  }
}

function confirmDisconnection(event) {
  confirm.require({
    target: event.currentTarget,
    message:
      'Déconnecter ce compte Google Drive ? Les dossiers ne seront plus surveillés ; les documents déjà importés restent dans votre base.',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Annuler', severity: 'secondary', outlined: true },
    acceptProps: { label: 'Déconnecter', severity: 'danger' },
    accept: () => disconnect(),
  })
}

async function disconnect() {
  rejections.value = []
  busy.value = true
  try {
    await disconnectDrive(auth.token)
    driveConnection.value = null
    watchedFolders.value = []
    picking.value = false
  } catch (error) {
    await handle(error)
  } finally {
    busy.value = false
  }
}

async function watchFolder(folder) {
  rejections.value = []
  busy.value = true
  try {
    // The 201 has no body: it is the list of folders that gives their complete state.
    await watchDriveFolder(auth.token, folder.id)
    picking.value = false
    watchedFolders.value = await listWatchedFolders(auth.token)
  } catch (error) {
    await handle(error)
  } finally {
    busy.value = false
  }
}

function confirmUnwatch(event, folder) {
  confirm.require({
    target: event.currentTarget,
    message: `Ne plus surveiller « ${folder.name} » ? Les documents déjà importés restent dans votre base.`,
    icon: 'pi pi-exclamation-triangle',
    rejectProps: { label: 'Annuler', severity: 'secondary', outlined: true },
    acceptProps: { label: 'Retirer', severity: 'danger' },
    accept: () => unwatch(folder),
  })
}

async function unwatch(folder) {
  rejections.value = []
  busy.value = true
  try {
    await unwatchDriveFolder(auth.token, folder.id)
    watchedFolders.value = await listWatchedFolders(auth.token)
  } catch (error) {
    await handle(error)
  } finally {
    busy.value = false
  }
}

async function importFolder(folder) {
  rejections.value = []
  busy.value = true
  try {
    await importWatchedFolder(auth.token, folder.id)
    remainingImportRefreshes = IMPORT_REFRESHES
    await load()
  } catch (error) {
    await handle(error)
  } finally {
    busy.value = false
  }
}

async function upload({ files }) {
  rejections.value = []
  duplicateIds.value = []
  busy.value = true
  try {
    // Sequentially: the route takes one file, and a burst of parallel uploads would race on
    // the duplicate check that precedes each write. A lost session stops the sequence: the
    // files that follow would only collect 401s.
    for (const file of files) {
      if (!(await uploadOne(file))) {
        return
      }
    }
    // The 201 has no body: it is the list that gives the complete state of the base.
    await load()
  } finally {
    busy.value = false
    // `clear()` empties the component's messages along with its files, and those messages name
    // files that never left: they are drained into ours first, so that one list carries every
    // refusal. They already carry the filename, hence no `filename` of our own.
    for (const message of uploader.value?.messages ?? []) {
      rejections.value.push({ filename: null, message })
    }
    // Resets the component, so that the same files can be selected again.
    uploader.value?.clear()
  }
}

// Answers whether the sequence may go on: a refused file does not stop it, a lost session does.
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
    } else if (error instanceof UnauthorizedError) {
      await handle(error)
      return false
    } else {
      // A 413 or a 415: the message is the server's and is displayed as is, under its file.
      rejections.value.push({ filename: file.name, message: error.message })
    }
  }
  return true
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

function formatDate(isoInstant) {
  return new Date(isoInstant).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })
}

onMounted(async () => {
  readDriveOutcome()
  await loadDrive()
  await load()
})
onUnmounted(() => {
  mounted = false
  clearTimeout(refreshTimer)
})
</script>

<template>
  <section class="documents">
    <PageTitle>Sources et documents</PageTitle>

    <section class="sources">
      <div class="source">
        <h3 class="source-name"><i class="pi pi-upload" /> Dépôt manuel</h3>
        <FileUpload
          ref="uploader"
          name="file"
          :accept="ACCEPTED_EXTENSIONS"
          :invalid-file-type-message="INVALID_FORMAT_MESSAGE"
          multiple
          custom-upload
          auto
          :show-upload-button="false"
          :show-cancel-button="false"
          choose-label="Déposer des documents"
          choose-icon="pi pi-upload"
          :disabled="busy"
          @uploader="upload"
        >
          <template #empty>
            <p class="source-hint">Glissez vos fichiers ici, ou cliquez pour les choisir.</p>
          </template>
        </FileUpload>
      </div>

      <div class="source">
        <header class="source-header">
          <h3 class="source-name"><i class="pi pi-google" /> Google Drive</h3>
          <Button
            v-if="driveConnection"
            type="button"
            label="Déconnecter"
            icon="pi pi-sign-out"
            text
            severity="danger"
            size="small"
            :disabled="busy"
            @click="confirmDisconnection"
          />
        </header>

        <template v-if="driveConnection">
          <p class="source-hint">Compte connecté : {{ driveConnection.googleEmail }}.</p>

          <Message v-if="needsReconnection" severity="warn">
            L'autorisation Google Drive n'est plus valide. Reconnectez le compte pour que les
            dossiers surveillés reprennent.
          </Message>

          <DriveSourceCard
            v-for="folder in watchedFolders"
            :key="folder.id"
            :folder="folder"
            :busy="busy"
            @import="importFolder(folder)"
            @unwatch="confirmUnwatch($event, folder)"
          />
          <p v-if="watchedFolders.length === 0" class="source-hint">
            Aucun dossier surveillé pour l'instant.
          </p>

          <DriveFolderPicker
            v-if="picking"
            :busy="busy"
            @select="watchFolder"
            @close="picking = false"
            @error="signOutIfRejected"
          />
          <div v-else class="source-buttons">
            <Button
              type="button"
              label="Ajouter un dossier"
              icon="pi pi-plus"
              text
              :disabled="busy"
              @click="picking = true"
            />
            <Button
              v-if="needsReconnection"
              type="button"
              label="Reconnecter le compte"
              icon="pi pi-google"
              :disabled="busy"
              @click="connect"
            />
          </div>
        </template>

        <template v-else>
          <p class="source-hint">
            Aucun compte connecté. Connectez-en un pour qu'un dossier alimente votre base tout seul
            — le dépôt manuel reste disponible.
          </p>
          <div class="source-buttons">
            <Button
              type="button"
              label="Connecter un compte Google Drive"
              icon="pi pi-google"
              :disabled="busy"
              @click="connect"
            />
          </div>
        </template>
      </div>
    </section>

    <Message v-if="driveOutcome" :severity="driveOutcome.severity" closable>
      {{ driveOutcome.text }}
    </Message>

    <Message v-for="(rejection, index) in rejections" :key="index" severity="error" closable>
      <span v-if="rejection.filename">« {{ rejection.filename }} » — </span>{{ rejection.message }}
    </Message>

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
.sources {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-md);
}

.source {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-sm);
}

.source-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sb-space-sm);
}

.source-name {
  margin: 0;
  font-size: var(--sb-section-title-size);
  display: flex;
  align-items: center;
  gap: var(--sb-space-xs);
}

.source-hint {
  margin: 0;
  color: var(--p-text-muted-color);
}

.source-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sb-space-sm);
}

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
