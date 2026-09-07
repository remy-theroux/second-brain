<script setup>
// Screen title: one per view. The `h1` lives here so that its size is decided in a
// single place, by the `--sb-title-size` token.
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
