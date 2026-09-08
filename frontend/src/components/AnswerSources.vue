<script setup>
import { RouterLink } from 'vue-router'

const props = defineProps({
  sources: { type: Array, required: true },
  // Numbers of the sources shown unfolded. The component holds nothing: following a
  // citation in the text opens a source, so the caller owns that state.
  opened: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:opened'])

function toggle(number) {
  emit(
    'update:opened',
    props.opened.includes(number)
      ? props.opened.filter((candidate) => candidate !== number)
      : [...props.opened, number],
  )
}
</script>

<template>
  <section v-if="sources.length" class="answer-sources" aria-label="Sources">
    <p class="answer-sources-title">Sources</p>

    <article v-for="source in sources" :key="source.number" class="answer-source">
      <button
        type="button"
        class="answer-source-header"
        :aria-expanded="opened.includes(source.number)"
        @click="toggle(source.number)"
      >
        <span
          :class="opened.includes(source.number) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"
        />
        <span class="answer-source-number">[{{ source.number }}]</span>
        <span>{{ source.filename }}</span>
        <span v-if="source.heading" class="answer-source-heading">— {{ source.heading }}</span>
      </button>

      <div v-if="opened.includes(source.number)" class="answer-source-body">
        <blockquote class="answer-source-excerpt">{{ source.text }}</blockquote>
        <RouterLink :to="{ name: 'document', params: { id: source.documentId } }">
          Voir le document
        </RouterLink>
      </div>
    </article>
  </section>
</template>

<style scoped>
.answer-sources {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
  padding-top: var(--sb-space-sm);
  border-top: 1px solid var(--p-content-border-color);
}

.answer-sources-title {
  margin: 0;
  font-size: var(--sb-text-small);
  text-transform: uppercase;
  color: var(--p-text-muted-color);
}

.answer-source-header {
  display: flex;
  align-items: center;
  gap: var(--sb-space-xs);
  width: 100%;
  padding: var(--sb-space-xs) 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--p-text-color);
  text-align: left;
  cursor: pointer;
}

.answer-source-number {
  color: var(--p-primary-color);
}

.answer-source-heading {
  color: var(--p-text-muted-color);
}

.answer-source-body {
  display: flex;
  flex-direction: column;
  gap: var(--sb-space-xs);
  padding: 0 0 var(--sb-space-sm) var(--sb-space-lg);
}

.answer-source-excerpt {
  margin: 0;
  padding-left: var(--sb-space-sm);
  border-left: 2px solid var(--p-content-border-color);
  color: var(--p-text-muted-color);
  white-space: pre-wrap;
}
</style>
