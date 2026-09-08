<script setup>
import { computed } from 'vue'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

const props = defineProps({
  folder: { type: Object, required: true },
  busy: { type: Boolean, default: false },
})

// The screen keeps the confirmations and the signing out: this card only says what became of
// a folder, and asks for the two gestures it carries. `unwatch` hands back the DOM event, which
// is what the confirmation anchors itself on.
defineEmits(['import', 'unwatch'])

// The outcome of an import travels as a code, like every enum the API serialises; the label is
// a screen matter — ADR-0022. The reason of a failure, on the other hand, comes from the server
// and is displayed as is.
const IMPORT_LABELS = {
  SUCCEEDED: 'Synchronisé',
  FAILED: 'Synchronisation en échec',
}

const IMPORT_SEVERITIES = {
  SUCCEEDED: 'success',
  FAILED: 'danger',
}

// A folder never imported carries NO outcome: the three fields are left out of the body, so it
// is their absence that says so — never a null value to test.
const neverImported = computed(() => !props.folder.lastImportAt)

const documentCount = computed(() => {
  const count = props.folder.documentCount
  if (!count) {
    return 'Aucun document apporté'
  }
  return count === 1 ? '1 document apporté' : `${count} documents apportés`
})

function formatDate(isoInstant) {
  return new Date(isoInstant).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })
}
</script>

<template>
  <article class="drive-source">
    <header class="drive-source-header">
      <h4 class="drive-source-name"><i class="pi pi-folder" /> {{ folder.name }}</h4>
      <div class="drive-source-actions">
        <Button
          type="button"
          icon="pi pi-refresh"
          text
          rounded
          :disabled="busy"
          :aria-label="`Synchroniser ${folder.name}`"
          @click="$emit('import')"
        />
        <Button
          type="button"
          icon="pi pi-times"
          severity="danger"
          text
          rounded
          :disabled="busy"
          :aria-label="`Ne plus surveiller ${folder.name}`"
          @click="$emit('unwatch', $event)"
        />
      </div>
    </header>

    <p class="drive-source-state">
      <Tag v-if="neverImported" value="Jamais synchronisé" severity="secondary" />
      <Tag
        v-else
        :value="IMPORT_LABELS[folder.lastImportStatus] ?? folder.lastImportStatus"
        :severity="IMPORT_SEVERITIES[folder.lastImportStatus] ?? 'secondary'"
      />
      <span class="drive-source-detail">
        {{ documentCount
        }}<template v-if="!neverImported">
          — dernière synchronisation le {{ formatDate(folder.lastImportAt) }}</template
        >
      </span>
    </p>

    <Message v-if="folder.lastImportError" severity="error" size="small" variant="simple">
      {{ folder.lastImportError }}
    </Message>

    <div v-if="folder.rejections?.length" class="drive-source-rejections">
      <p class="drive-source-detail">Fichiers écartés :</p>
      <ul>
        <li v-for="(rejection, index) in folder.rejections" :key="index">
          « {{ rejection.filename }} » — {{ rejection.reason }}
        </li>
      </ul>
    </div>
  </article>
</template>

<style scoped>
.drive-source {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
  padding: var(--sb-space-sm) var(--sb-space-md);
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
}

.drive-source-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sb-space-sm);
}

.drive-source-name {
  margin: 0;
  font-size: 1em;
  display: flex;
  align-items: center;
  gap: var(--sb-space-xs);
}

.drive-source-actions {
  display: flex;
  flex-shrink: 0;
}

.drive-source-state {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sb-space-xs);
}

.drive-source-detail {
  margin: 0;
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}

.drive-source-rejections ul {
  margin: var(--sb-space-xs) 0 0;
  padding-left: var(--sb-space-md);
  font-size: var(--sb-text-small);
  color: var(--p-text-muted-color);
}
</style>
