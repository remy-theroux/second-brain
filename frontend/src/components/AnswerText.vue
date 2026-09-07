<script setup>
import { computed } from 'vue'
import { answerSegments } from '@/components/answerSegments'

const props = defineProps({
  text: { type: String, required: true },
  sources: { type: Array, default: () => [] },
})

defineEmits(['citation'])

const segments = computed(() => answerSegments(props.text, props.sources))
</script>

<template>
  <p class="answer-text">
    <template v-for="(segment, index) in segments" :key="index">
      <button
        v-if="segment.number"
        type="button"
        class="answer-citation"
        :aria-label="`Voir la source ${segment.number}`"
        @click="$emit('citation', segment.number)"
      >
        {{ segment.text }}
      </button>
      <template v-else>{{ segment.text }}</template>
    </template>
  </p>
</template>

<style scoped>
.answer-text {
  margin: 0;
  /* The answer's line breaks are the only structure it has: they are kept as sent. */
  white-space: pre-wrap;
}

.answer-citation {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--p-primary-color);
  cursor: pointer;
}

.answer-citation:hover {
  text-decoration: underline;
}
</style>
