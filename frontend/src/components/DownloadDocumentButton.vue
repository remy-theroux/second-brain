<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import { fetchDocumentContent } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  documentId: { type: String, required: true },
  filename: { type: String, required: true },
  disabled: { type: Boolean, default: false },
})

// The screen keeps the signing out: a shared component does not push a route. It only says
// that the call failed, and the view passes the error to its own handler.
const emit = defineEmits(['error'])

const auth = useAuthStore()
const busy = ref(false)

// The token travels in a header (ADR-0003), so a plain link would fetch nothing: the body is
// read by `fetch`, then handed to the browser through a temporary anchor.
async function download() {
  busy.value = true
  try {
    save(await fetchDocumentContent(auth.token, props.documentId))
  } catch (error) {
    emit('error', error)
  } finally {
    busy.value = false
  }
}

function save(blob) {
  const url = URL.createObjectURL(blob)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = props.filename
  window.document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // Revoked on the next tick: revoking within the same one cancels the download the click
  // has only just started, in several browsers.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
</script>

<template>
  <Button
    type="button"
    icon="pi pi-download"
    text
    rounded
    :loading="busy"
    :disabled="disabled || busy"
    :aria-label="`Télécharger ${filename}`"
    @click="download"
  />
</template>
