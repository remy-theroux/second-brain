<script setup>
import Message from 'primevue/message'

// Le champ ne rend que le libellé et le message d'erreur ; l'input lui-même vient par le
// slot, parce que sa forme varie (InputText, Password…) et que ses attributs — `id` ou
// `input-id`, `invalid`, `autocomplete` — restent à la charge de la vue. Le composant ne
// devine pas comment son enfant s'identifie : `id` sert ici au `for` du label, et la vue
// le repose sur l'input.
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
