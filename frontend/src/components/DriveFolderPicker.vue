<script setup>
import { computed, onMounted, ref } from 'vue'
import Breadcrumb from 'primevue/breadcrumb'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { browseDriveFolders } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

defineProps({
  busy: { type: Boolean, default: false },
})

// The screen keeps the signing out: a shared component does not push a route. `error` is what
// lets it do so, the panel showing the refusal on its own account.
const emit = defineEmits(['select', 'close', 'error'])

const auth = useAuthStore()

// A trail, not a tree: the walk happens one level at a time, by successive calls, and
// rebuilding a whole tree would mean sweeping the entire Drive. The trail holds the folders
// entered, from the root; empty, it IS the root.
const trail = ref([])
const folders = ref([])
const loading = ref(true)
const failure = ref(null)

const home = { icon: 'pi pi-home', command: () => openAt(0) }

const crumbs = computed(() =>
  trail.value.map((folder, index) => ({ label: folder.name, command: () => openAt(index + 1) })),
)

async function browse() {
  loading.value = true
  failure.value = null
  try {
    folders.value = await browseDriveFolders(auth.token, trail.value.at(-1)?.id ?? '')
  } catch (error) {
    folders.value = []
    // The 409 (no Drive connected, or an authorization that no longer holds) and the 503
    // (Google unreachable) belong to this panel and are shown in it; the 401 belongs to the
    // screen, which signs out.
    failure.value = error.message
    emit('error', error)
  } finally {
    loading.value = false
  }
}

async function enter(folder) {
  trail.value = [...trail.value, folder]
  await browse()
}

async function openAt(depth) {
  trail.value = trail.value.slice(0, depth)
  await browse()
}

onMounted(browse)
</script>

<template>
  <section class="folder-picker">
    <header class="folder-picker-header">
      <Breadcrumb :home="home" :model="crumbs" class="folder-picker-trail" />
      <Button
        type="button"
        icon="pi pi-times"
        text
        rounded
        aria-label="Fermer le choix d'un dossier"
        @click="$emit('close')"
      />
    </header>

    <p class="folder-picker-hint">
      Seuls les dossiers sont listés : surveiller un dossier apporte les fichiers qu'il contient.
    </p>

    <Message v-if="failure" severity="error" size="small" variant="simple">{{ failure }}</Message>
    <p v-else-if="loading" class="folder-picker-hint">Chargement des dossiers…</p>
    <p v-else-if="folders.length === 0" class="folder-picker-hint">
      Ce dossier n'en contient aucun autre.
    </p>
    <ul v-else class="folder-picker-list">
      <li v-for="folder in folders" :key="folder.id">
        <Button
          type="button"
          class="folder-picker-open"
          text
          icon="pi pi-folder"
          :label="folder.name"
          :disabled="busy"
          @click="enter(folder)"
        />
        <Button
          type="button"
          label="Surveiller"
          size="small"
          :disabled="busy"
          :aria-label="`Surveiller ${folder.name}`"
          @click="$emit('select', folder)"
        />
      </li>
    </ul>
  </section>
</template>

<style scoped>
.folder-picker {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
  padding: var(--sb-space-sm) var(--sb-space-md);
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
}

.folder-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sb-space-sm);
}

.folder-picker-trail {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  padding: 0;
}

.folder-picker-hint {
  margin: 0;
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.folder-picker-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: var(--sb-picker-max-height);
  overflow-y: auto;
}

.folder-picker-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sb-space-sm);
}

.folder-picker-open {
  flex: 1;
  justify-content: flex-start;
  min-width: 0;
}
</style>
