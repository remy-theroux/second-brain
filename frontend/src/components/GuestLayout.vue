<script setup>
import Card from 'primevue/card'
</script>

<template>
  <div class="guest-layout">
    <Card class="guest-card">
      <template #content>
        <slot />
      </template>
    </Card>
  </div>
</template>

<style scoped>
.guest-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--sb-space-md);
}

.guest-card {
  width: 100%;
  max-width: var(--sb-guest-width);
}
</style>
