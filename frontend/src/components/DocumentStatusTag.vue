<script setup>
import Tag from 'primevue/tag'

// The status travels as a code, like everything the API serialises from an enum; the
// label is a screen matter, and this copy is assumed — ADR-0022. It lives here and not
// in a view because two screens display it: the list and the detail. The failure reason,
// on the other hand, comes from the server and is displayed as is — the front rewrites none.
const LABELS = {
  PENDING: 'En attente de traitement',
  EXTRACTED: 'Texte extrait',
  READY: 'Prêt à être interrogé',
  FAILED: 'Traitement en échec',
}

// The severity is a rendering decision, not data: "pending" is neither a success nor an
// error. `EXTRACTED` moves from `success` to `info`: it is no longer an outcome but a
// step, and `READY` now carries the green.
const SEVERITIES = {
  PENDING: 'secondary',
  EXTRACTED: 'info',
  READY: 'success',
  FAILED: 'danger',
}

defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <Tag :value="LABELS[status] ?? status" :severity="SEVERITIES[status] ?? 'secondary'" />
</template>
