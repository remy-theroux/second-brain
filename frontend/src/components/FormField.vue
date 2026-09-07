<script setup>
import Message from 'primevue/message'

// The field only renders the label and the error message; the input itself comes through the
// slot, because its shape varies (InputText, Password…) and its attributes — `id` or
// `input-id`, `invalid`, `autocomplete` — remain the view's responsibility. The component does
// not guess how its child identifies itself: `id` is used here for the label's `for`, and the
// view puts it back on the input.
defineProps({
  id: { type: String, required: true },
  label: { type: String, required: true },
  error: { type: String, default: '' },
})
</script>

<template>
  <div class="form-field">
    <label :for="id">{{ label }}</label>
    <slot />
    <Message v-if="error" severity="error" size="small" variant="simple">{{ error }}</Message>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
}
</style>
