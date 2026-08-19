<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AuthenticatedLayout from '@/components/AuthenticatedLayout.vue'
import GuestLayout from '@/components/GuestLayout.vue'

const route = useRoute()
// Le layout se déduit des métas d'authentification déjà portées par les routes : une méta
// `layout` dédiée dirait deux fois la même chose. Le layout invité est le défaut.
const layout = computed(() => (route.meta.requiresAuth ? AuthenticatedLayout : GuestLayout))
</script>

<template>
  <component :is="layout">
    <RouterView />
  </component>
</template>
