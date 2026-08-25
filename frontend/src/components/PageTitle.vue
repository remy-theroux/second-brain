<script setup>
// Titre d'écran : un seul par vue. Le `h1` vit ici pour que sa taille ne soit décidée
// qu'à un endroit, par le token `--sb-title-size`.
</script>

<template>
  <h1 class="page-title"><slot /></h1>
</template>

<style scoped>
.page-title {
  margin: 0;
  font-size: var(--sb-title-size);
}
</style>
