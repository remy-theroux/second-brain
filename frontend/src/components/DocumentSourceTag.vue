<script setup>
import Tag from 'primevue/tag'

defineProps({
  source: { type: String, required: true },
  // Absent from the body when the document was uploaded by hand, and absent too when Drive
  // handed back no link: the identifier of the file is not exposed, the link is.
  driveLink: { type: String, default: null },
})

// The source travels as a code, like every enum the API serialises; the label is a screen
// matter — ADR-0022.
const LABELS = {
  MANUAL: 'Dépôt manuel',
  GOOGLE_DRIVE: 'Google Drive',
}

const ICONS = {
  MANUAL: 'pi pi-upload',
  GOOGLE_DRIVE: 'pi pi-google',
}
</script>

<template>
  <span class="document-source">
    <Tag :value="LABELS[source] ?? source" :icon="ICONS[source]" severity="secondary" />
    <!-- A third-party URL: `noopener` is not decorative, it denies the opened page any hold
         on the one it came from. -->
    <a
      v-if="driveLink"
      class="document-source-link"
      :href="driveLink"
      target="_blank"
      rel="noopener"
    >
      Ouvrir dans Drive <i class="pi pi-external-link" />
    </a>
  </span>
</template>

<style scoped>
.document-source {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sb-space-xs);
}

.document-source-link {
  font-size: var(--sb-text-small);
  color: var(--p-primary-color);
}

.document-source-link .pi {
  font-size: var(--sb-text-small);
}
</style>
