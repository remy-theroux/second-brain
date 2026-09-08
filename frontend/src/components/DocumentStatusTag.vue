<script setup>
import Tag from 'primevue/tag'
import { STATUS_LABELS, STATUS_SEVERITIES } from './documentStatus'

defineProps({
  status: { type: String, required: true },
})
</script>

<template>
  <Tag
    :value="STATUS_LABELS[status] ?? status"
    :severity="STATUS_SEVERITIES[status] ?? 'secondary'"
  />
</template>
